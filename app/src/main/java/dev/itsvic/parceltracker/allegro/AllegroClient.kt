// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import android.os.Build
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

internal class AllegroClient(
    session: AllegroSession? = null,
    clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder(),
    debugInterceptor: Interceptor? = null,
    private val allegroUrl: HttpUrl = "https://allegro.pl/".toHttpUrl(),
    private val edgeUrl: HttpUrl = "https://edge.allegro.pl/".toHttpUrl(),
) {
  private val cookies = CapturingCookieJar()
  private val client =
      clientBuilder
          .apply { debugInterceptor?.let(::addInterceptor) }
          .cookieJar(cookies)
          .followRedirects(false)
          .callTimeout(20, TimeUnit.SECONDS)
          .build()
  private val moshi = Moshi.Builder().build()
  private val credentialsAdapter = moshi.adapter(CredentialsRequest::class.java)
  private val loginAdapter = moshi.adapter(LoginResponse::class.java)
  private val anyAdapter = moshi.adapter(Any::class.java)
  var session: AllegroSession? = session
    private set

  init {
    session?.let {
      cookies.seed("wdctx", it.wdctx)
      cookies.seed("datadome", it.datadome)
      it.qxlsessid?.let { value -> cookies.seed("QXLSESSID", value) }
    }
  }

  suspend fun login(username: String, password: String): AllegroSession {
    cookies.clear()
    val check =
        execute(
            Request.Builder()
                .url(allegroUrl.newBuilder().addPathSegment("client-check").build())
                .header("User-Agent", CLIENT_CHECK_USER_AGENT)
                .head()
                .build())
    if (check.status !in 200..299) {
      throw AllegroAuthenticationException(
          "Allegro client check failed. This connection may have been rejected.", check.status)
    }
    val wdctx = cookies.value("wdctx")
    val datadome = cookies.value("datadome")
    if (wdctx.isNullOrBlank() || datadome.isNullOrBlank()) {
      throw AllegroAuthenticationException("Allegro did not issue the cookies required to log in.")
    }

    val initialization =
        execute(
            requestBuilder(
                    edgeUrl
                        .newBuilder()
                        .addPathSegments("authentication/initialization/mobile")
                        .build(),
                    authHeaders(wdctx, datadome),
                )
                .post(ByteArray(0).toRequestBody(null))
                .build())
    if (initialization.status !in 200..299) {
      throw AllegroAuthenticationException(
          "Allegro login initialization failed (HTTP ${initialization.status}).",
          initialization.status,
      )
    }
    val qxlsessid = cookies.value("QXLSESSID")
    val currentDatadome = cookies.value("datadome") ?: datadome
    if (qxlsessid.isNullOrBlank()) {
      throw AllegroAuthenticationException("Allegro did not initialize the login session.")
    }

    val body = credentialsAdapter.toJson(CredentialsRequest(username, password))
    val verification =
        execute(
            requestBuilder(
                    edgeUrl
                        .newBuilder()
                        .addPathSegments("authentication/credentials/mobile/verification")
                        .build(),
                    authHeaders(wdctx, currentDatadome, qxlsessid),
                )
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build())
    if (verification.status != 200) {
      throw AllegroAuthenticationException(
          "Login failed (HTTP ${verification.status}). Check your credentials or account verification requirements.",
          verification.status,
      )
    }
    val response =
        verification.body?.let(loginAdapter::fromJson)
            ?: throw AllegroAuthenticationException("Allegro returned an invalid login response.")
    if (response.accessToken.isNullOrBlank()) {
      throw AllegroAuthenticationException(
          "Allegro did not return an access token. This account may require a CAPTCHA or another verification step.")
    }

    return AllegroSession(
            username = response.username ?: username,
            accessToken = response.accessToken,
            accessTokenExpiration = response.accessTokenExpiration?.toString(),
            wdctx = cookies.value("wdctx") ?: wdctx,
            datadome = cookies.value("datadome") ?: currentDatadome,
            qxlsessid = cookies.value("QXLSESSID") ?: qxlsessid,
        )
        .also { session = it }
  }

  suspend fun fetchDashboard(): List<AllegroPackage> {
    getJson("packages/summary", SUMMARY_MEDIA_TYPE)
    var lastError: AllegroException? = null
    listOf(PACKAGES_ROUTE, LEGACY_PACKAGES_ROUTE).forEach { route ->
      try {
        val payload = getRenderJson(route)
        val errorCode = mboxErrorCode(payload)
        if (errorCode == null) return AllegroParser.parsePackages(payload)
        lastError = AllegroException("Allegro returned a package-list error page.", errorCode)
      } catch (error: AllegroException) {
        lastError = error
        if (error.statusCode !in FALLBACK_STATUS_CODES) throw error
      }
    }
    throw lastError ?: AllegroException("No Allegro package route is available.")
  }

  suspend fun fetchPackageDetails(packageItem: AllegroPackage): AllegroPackage {
    val carrierId = packageItem.carrierId.ifBlank { carrierId(packageItem.carrier) }
    val tracking = packageItem.trackingNumber.ifBlank { packageItem.packageId }
    if (carrierId.isBlank() || tracking.isBlank()) {
      throw AllegroException("This package has no carrier or tracking number.")
    }
    val route =
        PACKAGES_ROUTE +
            "/szczegoly-dostawy?carrierId=" +
            encodeQueryValue(carrierId) +
            "&waybill=" +
            encodeQueryValue(tracking)
    val payload = getRenderJson(route)
    mboxErrorCode(payload)?.let {
      throw AllegroException("Allegro returned a package-details error page.", it)
    }
    return AllegroParser.parsePackageDetails(payload, packageItem)
  }

  suspend fun fetchPickupDetails(carrierId: String, waybill: String): AllegroPickupDetails {
    if (carrierId.isBlank() || waybill.isBlank()) {
      throw AllegroException("This package has no carrier or tracking number.")
    }
    val url =
        edgeUrl
            .newBuilder()
            .addPathSegments("packages/carrier")
            .addPathSegment(carrierId)
            .addPathSegment("waybill")
            .addPathSegment(waybill)
            .addPathSegment("pickup-details")
            .build()
    val payload = apiJson(url, PICKUP_MEDIA_TYPE)
    val responseWaybill = payload["waybill"]?.toString().orEmpty()
    val responseCarrier = payload["carrierId"]?.toString().orEmpty()
    if (responseWaybill.isNotBlank() && !responseWaybill.equals(waybill, ignoreCase = true)) {
      throw AllegroException("Allegro returned pickup details for a different package.")
    }
    if (responseCarrier.isNotBlank() && !responseCarrier.equals(carrierId, ignoreCase = true)) {
      throw AllegroException("Allegro returned pickup details for a different carrier.")
    }
    val details =
        AllegroPickupDetails(
            waybill = responseWaybill.ifBlank { waybill },
            carrierId = responseCarrier.ifBlank { carrierId },
            carrierName = payload["carrierName"]?.toString().orEmpty(),
            code = payload["formattedCode"]?.toString().orEmpty(),
            phoneNumber = payload["formattedPhoneNumber"]?.toString().orEmpty(),
            qrPayload = payload["qrCode"]?.toString().orEmpty(),
        )
    if (details.code.isBlank() && details.phoneNumber.isBlank() && details.qrPayload.isBlank()) {
      throw AllegroException("Allegro did not return pickup credentials for this package.")
    }
    return details
  }

  private suspend fun getRenderJson(route: String): Map<*, *> {
    val url =
        edgeUrl
            .newBuilder()
            .addPathSegments("mobile/render")
            .addQueryParameter("route", route)
            .build()
    return apiJson(url, RENDER_MEDIA_TYPE)
  }

  private suspend fun getJson(path: String, accept: String): Map<*, *> =
      apiJson(edgeUrl.newBuilder().addPathSegments(path).build(), accept)

  private suspend fun apiJson(url: HttpUrl, accept: String): Map<*, *> {
    val current =
        session ?: throw AllegroSessionExpiredException("Log in to your Allegro account first.")
    val response = execute(requestBuilder(url, apiHeaders(current, accept)).get().build())
    refreshSessionCookies()
    if (response.status == 401) {
      throw AllegroSessionExpiredException(
          "Your Allegro session expired. Log in again.", response.status)
    }
    if (response.status == 403) {
      throw AllegroException(
          "Allegro temporarily blocked the request. Try syncing again later.", response.status)
    }
    if (response.status !in 200..299) {
      throw AllegroException("Allegro request failed (HTTP ${response.status}).", response.status)
    }
    val payload =
        response.body?.let(anyAdapter::fromJson)
            ?: throw AllegroException("Allegro returned an empty response.", response.status)
    return payload as? Map<*, *>
        ?: throw AllegroException("Allegro returned an unexpected response.", response.status)
  }

  private suspend fun execute(request: Request): HttpResult =
      withTimeout(REQUEST_TIMEOUT_MILLIS) {
        client.newCall(request).executeAsync().use { response ->
          HttpResult(response.code, response.body.string())
        }
      }

  private fun refreshSessionCookies() {
    session =
        session?.copy(
            wdctx = cookies.value("wdctx") ?: session!!.wdctx,
            datadome = cookies.value("datadome") ?: session!!.datadome,
            qxlsessid = cookies.value("QXLSESSID") ?: session!!.qxlsessid,
        )
  }

  private fun requestBuilder(url: HttpUrl, headers: Map<String, String>): Request.Builder =
      Request.Builder().url(url).apply { headers.forEach(::header) }

  private fun authHeaders(
      wdctx: String,
      datadome: String,
      qxlsessid: String? = null,
  ): Map<String, String> =
      commonHeaders("application/json") +
          mapOf(
              "Cookie" to
                  buildString {
                    append("datadome=$datadome")
                    qxlsessid?.let { append("; QXLSESSID=$it") }
                  },
              "x-wdctx" to wdctx,
          )

  private fun apiHeaders(session: AllegroSession, accept: String): Map<String, String> =
      commonHeaders(accept) +
          mapOf(
              "Cookie" to
                  buildString {
                    append("datadome=${session.datadome}")
                    append("; wdctx=${session.wdctx}")
                    session.qxlsessid?.let { append("; QXLSESSID=$it") }
                  },
              "x-wdctx" to session.wdctx,
          )

  private fun commonHeaders(accept: String) =
      mapOf(
          "Accept" to accept,
          "Accept-Language" to "en-US",
          "Content-Type" to accept,
          "User-Agent" to userAgent(),
      )

  private fun userAgent(): String {
    val androidVersion = Build.VERSION.RELEASE.let { if ('.' in it) it else "$it.0" }
    return "pl.allegro/$ALLEGRO_APP_VERSION (Client-Id $ALLEGRO_CLIENT_ID) Android/$androidVersion (${Build.MANUFACTURER} ${Build.MODEL})"
  }

  private fun mboxErrorCode(value: Any?): Int? {
    if (value is Map<*, *>) {
      if (value["isErrorPage"] == true) {
        return (value["errorCode"] as? Number)?.toInt()
            ?: value["errorCode"]?.toString()?.toIntOrNull()
            ?: 500
      }
      value.values.forEach {
        mboxErrorCode(it)?.let { code ->
          return code
        }
      }
    } else if (value is List<*>) {
      value.forEach {
        mboxErrorCode(it)?.let { code ->
          return code
        }
      }
    }
    return null
  }

  private fun carrierId(carrier: String): String =
      when (carrier.lowercase()) {
        "allegro",
        "allegro one",
        "allegro one box" -> "ALLEGRO"
        "inpost" -> "INPOST"
        else -> carrier.uppercase().replace(' ', '_')
      }

  private fun encodeQueryValue(value: String): String =
      java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

  @JsonClass(generateAdapter = true)
  internal data class CredentialsRequest(val login: String, val password: String)

  @JsonClass(generateAdapter = true)
  internal data class LoginResponse(
      val accessToken: String? = null,
      val accessTokenExpiration: Any? = null,
      val username: String? = null,
  )

  private data class HttpResult(val status: Int, val body: String?)

  private class CapturingCookieJar : CookieJar {
    private val values = mutableMapOf<String, String>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
      cookies.forEach { values[it.name.lowercase()] = it.value }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()

    fun seed(name: String, value: String) {
      values[name.lowercase()] = value
    }

    fun value(name: String): String? = values[name.lowercase()]

    fun clear() = values.clear()
  }

  companion object {
    private const val CLIENT_CHECK_USER_AGENT = "okhttp/4.12.0"
    private const val ALLEGRO_APP_VERSION = "9.19.1"
    private const val ALLEGRO_CLIENT_ID = "e97de40c-0b60-4e81-808b-8eef2aa3cf3b"
    private const val PACKAGES_ROUTE = "https://allegro.pl/moje-allegro/zakupy/moje-przesylki"
    private const val LEGACY_PACKAGES_ROUTE = "$PACKAGES_ROUTE/app"
    private const val SUMMARY_MEDIA_TYPE = "application/vnd.allegro.internal.v2+json"
    private const val RENDER_MEDIA_TYPE = "application/vnd.allegro.internal.v1+json"
    private const val PICKUP_MEDIA_TYPE = "application/vnd.allegro.beta.v1+json"
    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    private val FALLBACK_STATUS_CODES = setOf(400, 404, 405, 406, 410, 422)
    private const val REQUEST_TIMEOUT_MILLIS = 25_000L
  }
}
