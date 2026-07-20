package dev.itsvic.parceltracker.api

import android.os.LocaleList
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

object FPXDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_4px
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private const val BASE_URL = "https://track.4px.com/"

  private val retrofit =
      Retrofit.Builder()
          .baseUrl(BASE_URL)
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  override fun acceptsFormat(trackingId: String): Boolean {
    val fourPxRegex = """^4PX[A-Z0-9]{16}$""".toRegex()
    return fourPxRegex.matchEntire(trackingId) != null
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val language = mapLanguageToAPIFormat(LocaleList.getDefault().get(0).language)

    val request = ParcelRequest(listOf(trackingId), language, "")

    val response =
        try {
          service.getParcel(request)
        } catch (_: Exception) {
          throw ParcelNonExistentException()
        }

    if (response.data.isEmpty()) {
      throw ParcelNonExistentException()
    }

    val parcelData = response.data.firstOrNull() ?: throw ParcelNonExistentException()

    return Parcel(
        trackingId,
        tracksToHistory(parcelData.tracks),
        trackCodeToStatus(parcelData.tracks.first().tkCode))
  }

  private fun mapLanguageToAPIFormat(language: String): String {
    return when (language) {
      "cn" -> "zh-cn"
      "en" -> "en-us"
      "de" -> "de-de"
      "ja" -> "ja-ja"
      "fr" -> "fr-fr"
      "es" -> "es-es"
      "pt" -> "pt-pt"
      else -> "en-us" // Fallback to English
    }
  }

  private fun trackCodeToStatus(trackCode: String): Status {
    // Couldn't find any official docs describing the track codes
    // Reference:
    // https://github.com/hrichiksite/trackpackapi/blob/46c6b32b101836ffe5d9903636eec10b6439ea0d/carriers/fourpx.js#L179

    if (listOf(
            "FPX_L_RPIF",
            "FPX_C_SPQS",
            "FPX_C_SPLS",
            "FPX_C_AAF",
            "FPX_C_RDFF",
            "FPX_C_ADFF",
            "FPX_F_AF",
            "FPX_F_DF",
            "FPX_Q_ECI",
            "FPX_Q_UEPC",
            "FPX_Q_ECC",
            "FPX_Q_AAEC",
            "FPX_Q_DFEC",
            "FPX_M_AAA",
            "FPX_M_DFA",
            "FPX_M_AAHK",
            "FPX_M_HA",
            "FPX_M_DAHK",
            "FPX_D_AAD",
            "FPX_D_DFD",
            "FPX_D_STPP",
            "FPX_D_HQ",
            "FPX_O_IR")
        .contains(trackCode)) {
      return Status.InTransit
    }

    if (listOf(
            "FPX_M_TDMF",
            "FPX_M_TDNF",
            "FPX_M_TDPH",
            "FPX_M_HCSC",
            "FPX_M_TDDB",
            "FPX_M_TD",
            "FPX_M_TDFM",
            "FPX_M_TDFC",
            "FPX_M_TDOL",
            "FPX_M_TDHM",
            "FPX_M_TDBW",
            "FPX_M_TDSC",
            "FPX_M_TDOP",
            "FPX_M_TDAC",
            "FPX_M_TDWF",
            "FPX_M_TDFD",
            "FPX_M_TDSA",
            "FPX_D_SHRP",
            "FPX_D_CCNS",
            "FPX_D_DD",
            "FPX_D_DDIA",
            "FPX_D_DDBT",
            "FPX_D_DDRH",
            "FPX_D_DDOH",
            "FPX_D_DDRR",
            "FPX_D_DDMD",
            "FPX_D_DDMS",
            "FPX_D_DDRS",
            "FPX_D_DDSR",
            "FPX_D_DDAR",
            "FPX_D_DDTP",
            "FPX_D_DDBW",
            "FPX_D_DDNF",
            "FPX_D_DDNI",
            "FPX_D_DDCA",
            "FPX_D_DDRC",
            "FPX_D_SHWD",
            "FPX_D_SHCC",
            "FPX_D_SHPD",
            "FPX_D_SHAP",
            "FPX_D_DDCC",
            "FPX_D_DDDC",
            "FPX_D_DDSS",
            "FPX_D_FDNC",
            "FPX_D_DFCR",
            "FPX_D_FDSD",
            "FPX_D_FDNR",
            "FPX_D_ADWP",
            "FPX_D_FDWP",
            "FPX_D_FDNR",
            "FPX_D_FDCA",
            "FPX_D_FDLO",
            "FPX_D_FDVO",
            "FPX_D_FDNC",
            "FPX_D_DFCR",
            "FPX_D_FDSD",
            "FPX_D_FDNR",
            "FPX_D_ADWP",
            "FPX_D_FDWP",
            "FPX_D_FDNR",
            "FPX_D_FDCA",
            "FPX_D_FDLO",
            "FPX_D_FDVO")
        .contains(trackCode)) {
      return Status.Delayed
    }

    if (listOf(
            "FPX_S_OK",
            "FPX_S_OKGP",
            "FPX_S_OKVP",
            "FPX_S_OKPO",
            "FPX_S_OKCC",
            "FPX_S_OKSC",
            "FPX_S_OKRC",
            "FPX_S_OKIDC")
        .contains(trackCode)) {
      return Status.Delivered
    }

    return when (trackCode) {
      "FPX_M_CRSD" -> Status.Customs
      "FPX_I_RCUK" -> Status.CustomsSuccess
      "FPX_D_SD" -> Status.OutForDelivery
      "FPX_S_OK" -> Status.Delivered
      else -> logUnknownStatus("4PX", trackCode)
    }
  }

  private fun tracksToHistory(tracks: List<Track>): List<ParcelHistoryItem> {
    return tracks.map { item ->
      ParcelHistoryItem(
          item.tkDesc,
          LocalDateTime.parse(item.tkDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
          item.tkLocation)
    }
  }

  private interface API {
    @POST("track/v2/front/listTrackV3")
    suspend fun getParcel(@Body data: ParcelRequest): ParcelResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class ParcelRequest(
      val queryCodes: List<String>,
      val language: String,
      val translateLanguage: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelResponse(
      val result: Int,
      val message: String,
      val data: List<ParcelData>,
      val tag: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelData(
      val queryCode: String,
      val serverCode: String,
      val shipperCode: String,
      val channelTrackCode: Any?,
      val ctStartCode: String,
      val ctEndCode: String,
      val ctEndName: String,
      val ctStartName: String,
      val status: Int,
      val duration: Int,
      val tracks: List<Track>,
      val hawbCodeSet: List<String>,
      val mutiPackage: Boolean,
      val masterOrderNum: Any?,
      val returnStatusFlag: Any?,
  )

  @JsonClass(generateAdapter = true)
  internal data class Track(
      val tkCode: String,
      val tkDesc: String,
      val tkLocation: String,
      val tkTimezone: String?,
      val tkDate: String,
      val tkDateStr: String,
      val tkCategoryCode: String?,
      val tkCategoryName: String?,
      val spTkSummary: Any?,
      val spTkZipCode: Any?,
      val tkTranslatedDesc: String,
      val tkTranslatedSummary: Any?,
      val sigPicUrl: Any?,
      val isSigPic: Any?,
  )

  @JsonClass(generateAdapter = true)
  internal data class ChannelContact(
      val channelCode: String,
      val channelSimpleName: String,
      val contact: String,
      val countryName: String,
      val customsPhone: String,
      val website: String,
      val workday: String,
  )
}
