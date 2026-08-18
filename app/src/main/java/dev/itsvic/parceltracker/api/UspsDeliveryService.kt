// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

// Reverse engineered from https://tools.usps.com/tracking/
object UspsDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_usps
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private const val BASE_URL = "https://tools.usps.com/"
  private const val TRACKING_URL = "https://tools.usps.com/go/TrackConfirmAction?qtc_tLabels1="
  private const val USER_AGENT =
      "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
  private const val INACTIVITY_TIMEOUT_MS = 1_500L
  private const val LOAD_TIMEOUT_MS = 10_000L

  private val digits20 = """^9\d{19}$""".toRegex()
  private val digits22 = """^9\d{21}$""".toRegex()
  private val digits26 = """^9\d{25}$""".toRegex()
  private val digits30 = """^9\d{29}$""".toRegex()

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a", Locale.US)
  private val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

  override fun acceptsFormat(trackingId: String): Boolean {
    return digits20.matches(trackingId) ||
        digits22.matches(trackingId) ||
        digits26.matches(trackingId) ||
        digits30.matches(trackingId) ||
        emsFormat.accepts(trackingId)
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    return parseParcelResponse(trackingId, fetchPlainHtml(trackingId))
  }

  override suspend fun getParcel(context: Context, trackingId: String, postalCode: String?): Parcel {
    // tools.usps.com is behind Akamai Bot Manager, which serves a JS ("_abck") challenge to plain
    // HTTP clients, so the fast path can come back 200 but with no tracking card. Fall back to
    // rendering the page in a WebView (a real Chromium engine) that solves the challenge natively.
    val plain =
        try {
          fetchPlainHtml(trackingId)
        } catch (_: Exception) {
          ""
        }
    if (containsTrackingCard(plain)) return parseParcelResponse(trackingId, plain)

    val rendered = fetchRenderedHtml(context, trackingId)
    if (containsTrackingCard(rendered)) return parseParcelResponse(trackingId, rendered)
    throw ParcelNonExistentException()
  }

  private suspend fun fetchPlainHtml(trackingId: String): String {
    val response = service.track(trackingId)
    if (!response.isSuccessful) throw HttpException(response)
    return response.body()?.string() ?: throw ParcelNonExistentException()
  }

  private fun containsTrackingCard(html: String): Boolean {
    if (html.isEmpty()) return false
    val document = Jsoup.parse(html)
    return document.selectFirst(".tb-step") != null ||
        document.selectFirst(".tracking-number") != null
  }

  private suspend fun fetchRenderedHtml(context: Context, trackingId: String): String {
    return suspendCancellableCoroutine { cont ->
      val done = java.util.concurrent.atomic.AtomicBoolean(false)
      var webViewRef: WebView? = null
      fun destroy() {
        webViewRef?.let { wv -> wv.post { wv.destroy() } }
        webViewRef = null
      }
      fun finish(html: String) {
        if (done.compareAndSet(false, true) && !cont.isCancelled) {
          cont.resume(html)
          destroy()
        }
      }
      fun finishWithError(e: Throwable) {
        if (done.compareAndSet(false, true) && !cont.isCancelled) {
          cont.resumeWithException(e)
          destroy()
        }
      }

      Handler(Looper.getMainLooper()).post {
        val webView = WebView(context.applicationContext)
        webViewRef = webView
        val mainHandler = Handler(Looper.getMainLooper())
        cont.invokeOnCancellation { destroy() }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = USER_AGENT
        webView.webViewClient =
            object : WebViewClient() {
              // Never touch third-party hosts (fonts, tag managers, analytics): answer them with
              // an empty response so the page is still fully functional without the extra network.
              override fun shouldInterceptRequest(
                  view: WebView?,
                  request: WebResourceRequest?,
              ): WebResourceResponse? {
                val host = request?.url?.host ?: return null
                return if (host == "tools.usps.com" || host == "www.usps.com") null
                else
                    WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        java.io.ByteArrayInputStream(ByteArray(0)))
              }

              override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame != false) {
                  finishWithError(RuntimeException(error?.description?.toString() ?: "WebView error"))
                }
              }

              override fun onReceivedHttpError(
                  view: WebView?,
                  request: WebResourceRequest?,
                  errorResponse: WebResourceResponse?,
              ) {
                if (request?.isForMainFrame != false) {
                  finishWithError(RuntimeException("HTTP ${errorResponse?.statusCode}"))
                }
              }

              // Akamai redirects the page (interstitial -> challenge -> /tracking/...), each step
              // being a full navigation that fires onPageFinished. Read the DOM once per finish and
              // stop as soon as the tracking card appears. The package data is server-rendered, so
              // no polling loop is needed - only an inactivity timeout in case nothing renders.
              override fun onPageFinished(view: WebView?, url: String?) {
                val wv = view ?: run { finish(""); return }
                wv.evaluateJavascript(
                    "(document.querySelector('.tb-step') || document.querySelector('.tracking-number')) !== null",
                ) { hasCard ->
                  if (hasCard == "true") {
                    wv.evaluateJavascript("document.documentElement.outerHTML") {
                      finish(decodeJsString(it))
                    }
                  } else {
                    // No card on this page (yet). If no further page-finish follows shortly (i.e.
                    // the redirects have stopped and this is a genuine "Tracking Not Available"
                    // page), release with the empty result. A new onPageFinished cancels and
                    // replaces this inactivity timeout.
                    if (done.get()) return@evaluateJavascript
                    mainHandler.removeCallbacksAndMessages(null)
                    mainHandler.postDelayed({ finish("") }, INACTIVITY_TIMEOUT_MS)
                  }
                }
              }
            }
        try {
          webView.loadUrl(TRACKING_URL + trackingId)
        } catch (e: Throwable) {
          finishWithError(e)
        }

        // Hard safety net: never hang the coroutine waiting on the WebView.
        mainHandler.postDelayed({ finish("") }, LOAD_TIMEOUT_MS)
      }
    }
  }

  private fun decodeJsString(value: String): String {
    if (value == "null" || value == "\"\"") return ""
    return try {
      org.json.JSONTokener(value).nextValue()?.toString() ?: ""
    } catch (_: Exception) {
      value.trim('\'', '"')
    }
  }

  internal fun parseParcelResponse(trackingId: String, html: String): Parcel {
    val document = Jsoup.parse(html)
    val steps = document.select(".tb-step:not(.toggle-history-container)")
    if (steps.isEmpty()) throw ParcelNonExistentException()

    val history =
        steps.mapNotNull { element ->
          val description = element.selectFirst(".tb-status-detail")?.text()?.trim().orEmpty()
          if (description.isEmpty() && element.selectFirst(".tb-status") == null) return@mapNotNull null
          val location = element.selectFirst(".tb-location")?.text()?.trim().orEmpty()
          val dateText =
              element
                  .select(".tb-date")
                  .firstOrNull()
                  ?.text()
                  ?.trim()
                  ?.replace("\\s+".toRegex(), " ")
                  .orEmpty()
          val time = parseDate(dateText) ?: return@mapNotNull null
          ParcelHistoryItem(
              description = if (description.isEmpty()) "Status update" else description,
              time = time,
              location = location,
          )
        }
    if (history.isEmpty()) throw ParcelNonExistentException()

    val currentStatus =
        document.selectFirst(".tb-step.current-step .tb-status")?.text()?.trim()
            ?: history.first().description
    val currentDetail =
        document.selectFirst(".tb-step.current-step .tb-status-detail")?.text()?.trim()
            ?: currentStatus
    val status = mapStatus(currentStatus, currentDetail) ?: logUnknownStatus("USPS", currentStatus)

    return Parcel(trackingId, history, status)
  }

  private fun parseDate(text: String): LocalDateTime? {
    if (text.isEmpty()) return null
    return try {
      LocalDateTime.parse(text, dateTimeFormatter)
    } catch (_: DateTimeParseException) {
      try {
        LocalDateTime.parse(text, dateFormatter).toLocalDate().atStartOfDay()
      } catch (_: DateTimeParseException) {
        null
      }
    }
  }

  private fun mapStatus(status: String, detail: String): Status? {
    val normalized = "$status $detail".lowercase(Locale.US)

    return when {
      normalized.contains("delivered") && normalized.contains("neighbor") ->
          Status.DeliveredToNeighbor
      normalized.contains("delivered") && normalized.contains("safe place") ->
          Status.DeliveredToASafePlace
      normalized.contains("delivered") -> Status.Delivered

      normalized.contains("out for delivery") -> Status.OutForDelivery

      normalized.contains("available for pickup") ||
          normalized.contains("held at post office") ||
          normalized.contains("ready for pickup") -> Status.AwaitingPickup

      normalized.contains("delivery attempted") ||
          normalized.contains("notice left") ||
          normalized.contains("no access") -> Status.DeliveryFailure

      normalized.contains("return to sender") ||
          normalized.contains("returning to") -> Status.ReturningToSender
      normalized.contains("returned to sender") -> Status.ReturnedToSender

      normalized.contains("arriving late") || normalized.contains("delayed") -> Status.Delayed

      normalized.contains("label created") ||
          normalized.contains("pre-shipment") ||
          normalized.contains("pre shipment") ||
          normalized.contains("awaiting item") ||
          normalized.contains("accepted") ||
          normalized.contains("origin post is preparing") -> Status.Preadvice

      normalized.contains("customs") && normalized.contains("held") -> Status.CustomsHeld
      normalized.contains("customs") && normalized.contains("clear") -> Status.CustomsSuccess
      normalized.contains("customs") -> Status.Customs

      normalized.contains("in transit") ||
          normalized.contains("arrived at usps") ||
          normalized.contains("departed usps") ||
          normalized.contains("processed through") ||
          normalized.contains("in possession") ||
          normalized.contains("arriving on time") -> Status.InTransit

      else -> null
    }
  }

  private val retrofit = Retrofit.Builder().baseUrl(BASE_URL).client(api_client).build()
  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("go/TrackConfirmAction")
    @Headers(
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language: en-US,en;q=0.9",
        "User-Agent: $USER_AGENT",
    )
    suspend fun track(@Query("qtc_tLabels1") trackingId: String): Response<ResponseBody>
  }
}
