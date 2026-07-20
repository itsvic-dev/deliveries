package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

object UkrposhtaDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_ukrposhta
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private const val API_KEY = "c3e02b53-3b1d-386e-b676-141ffa054c57"

  override fun acceptsFormat(trackingId: String): Boolean {
    return emsFormat.accepts(trackingId)
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val resp =
        try {
          service.getStatuses(trackingId)
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    val history =
        resp.reversed().map {
          ParcelHistoryItem(it.eventName, LocalDateTime.parse(it.date), "${it.name}, ${it.country}")
        }

    val status =
        when (resp.last().event) {
          10100 -> Status.Preadvice
          20700,
          20800,
          21500 -> Status.InTransit
          21700,
          21400 -> Status.InWarehouse
          31100,
          31300,
          31400 -> Status.DeliveryFailure
          41000,
          48000 -> Status.Delivered
          else -> logUnknownStatus("Ukrposhta", "${resp.last().event} (${resp.last().eventName}")
        }

    return Parcel(resp.first().barcode, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://www.ukrposhta.ua/status-tracking/0.0.1/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("statuses")
    @Headers("Authorization: Bearer $API_KEY")
    suspend fun getStatuses(
        @Query("barcode") trackingId: String,
        @Query("lang") lang: String = "en",
    ): List<ParcelStatus>
  }

  @JsonClass(generateAdapter = true)
  internal data class ParcelStatus(
      val barcode: String,
      val date: String, // LocalDateTime according to docs
      val name: String, // name of the post office
      val country: String, // country of shipment location
      val event: Int,
      val eventName: String,
  )
}
