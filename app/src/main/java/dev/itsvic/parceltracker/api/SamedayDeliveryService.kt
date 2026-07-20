// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.ZoneId
import java.time.ZonedDateTime
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path

object SamedayRomaniaDeliveryService : SamedayDeliveryService("ro", R.string.service_sameday_ro)

object SamedayHungaryDeliveryService : SamedayDeliveryService("hu", R.string.service_sameday_hu)

object SamedayBulgariaDeliveryService : SamedayDeliveryService("bg", R.string.service_sameday_bg)

open class SamedayDeliveryService(region: String, override val nameResource: Int) :
    DeliveryService {
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val resp =
        try {
          service.getAwbHistory(trackingId)
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    val history =
        resp.awbHistory.map {
          ParcelHistoryItem(
              it.status,
              // i'm sure there's a more concise way of doing this
              ZonedDateTime.parse(it.statusDate)
                  .toInstant()
                  .atZone(ZoneId.systemDefault())
                  .toLocalDateTime(),
              if (it.transitLocation.isNotBlank())
                  "${it.transitLocation}, ${it.county}, ${it.country}"
              else if (it.county.isNotBlank()) "${it.county}, ${it.country}"
              else if (it.country.isNotBlank()) it.country else "")
        }

    val status =
        when (val id = resp.awbHistory.first().statusStateId) {
          1 -> Status.Preadvice
          2 -> Status.InTransit
          7 -> Status.InTransit
          3 -> Status.InWarehouse
          4 -> Status.OutForDelivery
          18 -> Status.AwaitingPickup
          5 -> Status.Delivered
          else -> logUnknownStatus("Sameday", id.toString())
        }

    return Parcel(trackingId, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://api.sameday.${region}/api/public/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("awb/{id}/awb-history") suspend fun getAwbHistory(@Path("id") id: String): HistoryResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class HistoryResponse(
      val awbHistory: List<Event>,
  )

  @JsonClass(generateAdapter = true)
  internal data class Event(
      val country: String = "",
      val county: String = "",
      val status: String = "",
      val statusDate: String,
      val statusStateId: Int,
      val transitLocation: String = "",
  )
}
