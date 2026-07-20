package dev.itsvic.parceltracker.api

import android.os.LocaleList
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

// magyar fosta
object MagyarPostaDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_hungarian_post
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override fun acceptsFormat(trackingId: String): Boolean {
    val someWeirdFormat = """^\w{3}\d{2}\w{7}\d{8}$""".toRegex()
    val govFormat = """^RL\d{14}$""".toRegex()
    val parcelLockerFormat = """^PNTM\d{22}$""".toRegex()
    return someWeirdFormat.accepts(trackingId) ||
        emsFormat.accepts(trackingId) ||
        govFormat.accepts(trackingId) ||
        parcelLockerFormat.accepts(trackingId)
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val locale = LocaleList.getDefault().get(0).language
    val resp =
        try {
          service.getTrackingInfo(trackingId, language = if (locale == "hu") 1 else 2)
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    val events = resp[trackingId]
    if (events == null) {
      throw ParcelNonExistentException()
    }

    val history =
        events.map {
          ParcelHistoryItem(
              it.tranzakcioTipusLeiras,
              LocalDateTime.ofInstant(
                  Instant.ofEpochMilli(it.time), TimeZone.getDefault().toZoneId()),
              it.postaNev.replace('|', '\n'))
        }

    val category = events.first().tranzakcioKategoriaKod
    val detailedType = events.first().tranzakcioAzon

    val status =
        when (category) {
          "1" -> Status.Preadvice

          "2" ->
              when (detailedType) {
                "AVATS" -> Status.AwaitingPickup
                "30" -> Status.InWarehouse
                "31",
                "38",
                "NAV_KIADHATO_H7",
                "UERT18_ONLINE",
                "SIKERES_ONLINE_FIZ" -> Status.Customs
                else -> Status.InTransit
              }

          "3" ->
              when (detailedType) {
                "KLUVK" -> Status.DeliveryFailure
                else -> Status.InTransit
              }

          "4" ->
              when (detailedType) {
                "KIJKO" -> Status.OutForDelivery
                else -> Status.InTransit
              }

          "5" -> Status.Delivered
          else -> logUnknownStatus("Magyar Posta", "$category, $detailedType")
        }

    return Parcel(trackingId, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://posta.hu/szolgaltatasok/PostaWeb/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("TrackingInfo")
    suspend fun getTrackingInfo(
        @Query("ragszam") trackingId: String,
        @Query("language") language: Int = 2,
        @Query("registered") registered: Boolean = false,
    ): Map<String, List<TrackingEvent>>
  }

  @JsonClass(generateAdapter = true)
  internal data class TrackingEvent(
      val time: Long,
      val postaNev: String, // post office
      val tranzakcioKategoriaKod: String, // category code
      val tranzakcioTipusLeiras: String, // type description
      val tranzakcioAzon: String, // transaction ID, i think it's meant to be transaction type tho
  )
}
