package dev.itsvic.parceltracker.api

import android.os.LocaleList
import android.text.Html
import android.util.Log
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.misc.defaultRegionsForLanguageCode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

object UPSDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_ups
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val tokens = getCsrfTokens(trackingId)

    val language = LocaleList.getDefault().get(0)
    val country = language.country.ifEmpty { defaultRegionsForLanguageCode[language.language] }
    val locale = "${language.language}_$country"

    val resp =
        try {
          service.getStatus(
              locale,
              "X-CSRF-TOKEN=${tokens.first}",
              tokens.second,
              GetStatusRequest(locale, listOf(trackingId.lowercase())))
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    if (resp.trackDetails == null || resp.trackDetails.isEmpty()) {
      throw ParcelNonExistentException()
    }

    val details = resp.trackDetails.first()

    if (details.errorCode != null) {
      throw ParcelNonExistentException()
    }

    val history =
        details.shipmentProgressActivities!!.map {
          val gmtDate =
              LocalDateTime.parse(
                  "${it.gmtDate.subSequence(0, 4)}-${
                    it.gmtDate.subSequence(
                        4,
                        6
                    )
                }-${it.gmtDate.subSequence(6, 8)}T${it.gmtTime}",
                  DateTimeFormatter.ISO_DATE_TIME)
          val adjustedDate =
              gmtDate
                  .atOffset(ZoneOffset.UTC)
                  .withOffsetSameInstant(ZoneId.systemDefault().rules.getOffset(gmtDate))
                  .toLocalDateTime()

          ParcelHistoryItem(
              Html.fromHtml(it.activityScan, Html.FROM_HTML_MODE_LEGACY).toString(),
              adjustedDate,
              it.location)
        }

    val status =
        when (details.progressBarType) {
          "ManifestUpload" -> Status.Preadvice
          "FirstUPSPossession" -> Status.InTransit
          "InTransit" -> Status.InTransit
          "OutForDelivery" -> Status.OutForDelivery
          "Delivered" -> Status.Delivered
          "Exception" -> Status.DeliveryFailure
          else -> logUnknownStatus("UPS", details.progressBarType!!)
        }

    val metadata = mutableMapOf<Int, String>()
    if (details.additionalInformation?.weightUnit != null) {
      metadata[R.string.property_weight] =
          "${details.additionalInformation.weight} ${details.additionalInformation.weightUnit}"
    }

    // ETA
    if (details.scheduledDeliveryDateDetail != null && details.packageStatusTime != null) {
      val month =
          when (details.scheduledDeliveryDateDetail.monthCMSKey) {
            "cms.stapp.jan" -> 1
            "cms.stapp.feb" -> 2
            "cms.stapp.mar" -> 3
            "cms.stapp.apr" -> 4
            "cms.stapp.may" -> 5
            "cms.stapp.jun" -> 6
            "cms.stapp.jul" -> 7
            "cms.stapp.aug" -> 8
            "cms.stapp.sep" -> 9
            "cms.stapp.oct" -> 10
            "cms.stapp.nov" -> 11
            "cms.stapp.dec" -> 12
            else -> 0
          }
      val day = details.scheduledDeliveryDateDetail.dayNum.toInt()
      val date = LocalDate.now().withMonth(month).withDayOfMonth(day)
      val eta =
          (date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) +
              "\n"
              // cleanup A.M., P.M. -> AM, PM
              +
              details.packageStatusTime.replace(".M.", "M"))
      metadata[R.string.property_eta] = eta
    }

    return Parcel(trackingId, history, status, metadata)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://webapis.ups.com/track/api/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @Headers(
        "accept: application/json, text/plain, */*",
        "accept-language: en-US,en;q=0.9",
        "origin: https://www.ups.com",
        "sec-ch-ua: \"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"",
        "sec-ch-ua-mobile: ?0",
        "sec-ch-ua-platform: \"Linux\"",
        "sec-fetch-dest: empty",
        "sec-fetch-mode: cors",
        "sec-fetch-site: same-site",
        "user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
    )
    @POST("Track/GetStatus")
    suspend fun getStatus(
        @Query("loc") locale: String = "en_US",
        @Header("cookie") csrfTokenCookie: String,
        @Header("x-xsrf-token") xsrfToken: String,
        @Body data: GetStatusRequest
    ): GetStatusResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class GetStatusRequest(
      val Locale: String,
      val TrackingNumber: List<String>,
      val isBarcodeScanned: Boolean = false,
      val Requester: String = "st",
      val returnToValue: String = "",
  )

  @JsonClass(generateAdapter = true)
  internal data class GetStatusResponse(
      val trackDetails: List<TrackDetails>?,
  )

  @JsonClass(generateAdapter = true)
  internal data class TrackDetails(
      val errorCode: String?,
      val progressBarType: String?,
      val additionalInformation: PkgMoreInfo?,
      val shipmentProgressActivities: List<ActivityEntry>?,
      val scheduledDeliveryDateDetail: DeliveryDateDetail?,
      val packageStatusTime: String?,
  )

  @JsonClass(generateAdapter = true)
  internal data class PkgMoreInfo(
      val weight: String,
      val weightUnit: String?,
  )

  @JsonClass(generateAdapter = true)
  internal data class ActivityEntry(
      val location: String,
      val activityScan: String,
      val gmtDate: String,
      val gmtTime: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class DeliveryDateDetail(
      val monthCMSKey: String,
      val dayNum: String,
  )

  // Grabs the necessary tokens by making a request to the UPS website
  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun getCsrfTokens(trackingId: String): Pair<String, String> {
    val request =
        Request.Builder()
            .addHeader("accept", "*/*")
            .addHeader("accept-language", "en-US,en;q=0.9")
            .addHeader(
                "sec-ch-ua",
                "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"133\", \"Chromium\";v=\"133\"")
            .addHeader("sec-ch-ua-mobile", "?0")
            .addHeader("sec-ch-ua-platform", "\"Linux\"")
            .addHeader("sec-fetch-dest", "empty")
            .addHeader("sec-fetch-mode", "cors")
            .addHeader("sec-fetch-site", "same-site")
            .addHeader(
                "user-agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
            .url("https://www.ups.com/track?tracknum=${trackingId.lowercase()}")
            .build()

    api_client.newCall(request).executeAsync().use { response ->
      Log.d("UPS", "Got response")
      var csrfToken = ""
      var xsrfToken = ""
      for (pair in response.headers) {
        if (pair.first.lowercase() == "set-cookie") {
          if (pair.second.startsWith("X-CSRF-TOKEN=")) {
            csrfToken = pair.second.split('=')[1].split(';')[0]
            Log.d("UPS", "Got CSRF token: $csrfToken")
          }
          if (pair.second.startsWith("X-XSRF-TOKEN-ST=")) {
            xsrfToken = pair.second.split('=')[1].split(';')[0]
            Log.d("UPS", "Got XSRF token: $xsrfToken")
          }
        }
      }
      return Pair(csrfToken, xsrfToken)
    }

    return Pair("", "")
  }
}
