// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

object DhlGerDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_dhl_ger
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override fun acceptsFormat(trackingId: String): Boolean {
    val dhlParcelFormat = """^(?:JJD|JVGL|3S|JV|JD)\d*$""".toRegex()
    return digits11Format.accepts(trackingId) ||
        digits12Format.accepts(trackingId) ||
        digits18Format.accepts(trackingId) ||
        emsFormat.accepts(trackingId) ||
        dhlParcelFormat.accepts(trackingId)
  }

  override suspend fun getParcel(
    trackingId: String,
    postCode: String?
  ): Parcel {
    val resp =
        try {
          service.getShipments(trackingId, "en")
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    val shipment = resp.shipments.firstOrNull() { it.shipmentDetails.shippingHistory != null}
      ?: throw ParcelNonExistentException()

    val details = shipment.shipmentDetails
    val shippingHistory = requireNotNull(details.shippingHistory)

    val status = when {
      details.isDelivered -> Status.Delivered
      details.returnShipment -> Status.DeliveryFailure
      else -> when (shippingHistory.progress) {
        1 -> Status.Preadvice
        2, 3 -> Status.InTransit
        4 -> Status.OutForDelivery
        5 -> Status.Delivered
        else -> Status.Unknown
      }
    }

    val history = shippingHistory.events
        .sortedByDescending { it.date }
        .map {
      ParcelHistoryItem(
          it.status,
          LocalDateTime.parse(it.date, DateTimeFormatter.ISO_DATE_TIME),
          "Unknown location"
      )
    }

    return Parcel(shipment.id, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://www.dhl.de/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("int-verfolgen/data/search")
    suspend fun getShipments(
      @Query("piececode") trackingId: String,
      @Query("lang") language: String = "en",
      @Header("User-Agent") userAgent: String = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.7871.189 Mobile Safari/537.36"
    ): TrackingResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class TrackingResponse(
    @Json(name = "sendungen")
    val shipments: List<Shipment>,
  )

  @JsonClass(generateAdapter = true)
  internal data class Shipment(
    val id: String,
    @Json(name = "sendungsdetails")
    val shipmentDetails: ShipmentDetails,
  )

  @JsonClass(generateAdapter = true)
  internal data class ShipmentDetails(
    @Json(name = "sendungsverlauf")
    val shippingHistory: ShipmentHistory?,
    @Json(name = "istZugestellt")
    val isDelivered: Boolean,
    @Json(name = "ruecksendung")
    val returnShipment: Boolean,
  )

  @JsonClass(generateAdapter = true)
  internal data class ShipmentHistory(
    @Json(name = "fortschritt")
    val progress: Int,
    val status: String,
    val events: List<Event>,
  )

  @JsonClass(generateAdapter = true)
  internal data class Event(
    @Json(name = "datum")
    val date: String,
    val status: String,
    @Json(name = "ruecksendung")
    val returnShipment: Boolean,
  )
}
