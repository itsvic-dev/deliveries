package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.ZoneId
import java.time.ZonedDateTime
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path

object NovaPostDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_nova_poshta
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override fun acceptsFormat(trackingId: String): Boolean {
    val regex = """^\d{14}$""".toRegex()
    return regex.accepts(trackingId)
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val resp =
        try {
          service.getParcel(trackingId)
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    val history =
        resp.tracking.reversed().map {
          ParcelHistoryItem(
              it.event_name,
              ZonedDateTime.parse(it.date)
                  .withZoneSameInstant(ZoneId.systemDefault())
                  .toLocalDateTime(),
              it.settlement_name)
        }

    // very crude guesswork. Nova Post does not make this easy at all lol
    val status =
        when (resp.tracking.last().code) {
          "1" -> Status.Preadvice
          "4",
          "6",
          "5" -> Status.InTransit
          "7",
          "8",
          "9" -> Status.Delivered
          else -> logUnknownStatus("Nova Post", resp.tracking.last().code)
        }

    return Parcel(resp.number, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://api.novapost.com/site/v.1.0/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("shipments/tracking/{id}")
    suspend fun getParcel(@Path("id") trackingId: String): ParcelData
  }

  @JsonClass(generateAdapter = true)
  internal data class ParcelData(
      val number: String,
      val scheduled_delivery_date: String, // UTC ISO 8601 date
      val tracking: List<ParcelEvent>,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelEvent(
      val code: String,
      val event_name: String,
      val date: String, // UTC ISO 8601 date
      val settlement_name: String,
  )
}
