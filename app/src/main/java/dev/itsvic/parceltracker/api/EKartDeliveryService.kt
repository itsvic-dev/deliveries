package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

object EKartDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_ekart
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val resp =
        try {
          service.getTrackingDetails(GetTrackingDetailsRequest(trackingId))
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    // these are the same checks that the official tracking page does. roughly.
    val status =
        when {
          resp.shipmentTrackingDetails.last().statusDetails == "Delivered" -> Status.Delivered
          resp.shipmentTrackingDetails.last().statusDetails == "Out For Delivery" ->
              Status.OutForDelivery
          resp.reachedNearestHub == true -> Status.InWarehouse
          // assume that, if it hasn't reachedNearestHub yet, and it's moving, it's in transit
          // this is a check that's not done originally but we do it here for clarity's sake
          resp.faShipment == true && resp.shipmentTrackingDetails.size != 1 -> Status.InTransit
          resp.faShipment == true && resp.shipmentTrackingDetails.size == 1 -> Status.Preadvice
          else -> logUnknownStatus("eKart", resp.shipmentTrackingDetails.last().statusDetails)
        }

    val history =
        resp.shipmentTrackingDetails.reversed().map {
          ParcelHistoryItem(it.statusDetails, localDateFromMilli(it.date), it.city)
        }

    val properties =
        mapOf(
            (if (status == Status.Delivered) R.string.property_delivery_time
            else R.string.property_eta) to
                localDateFromMilli(resp.expectedDeliveryDate)
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)))

    return Parcel(trackingId, history, status, properties)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://ekartlogistics.com/ws/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @POST("getTrackingDetails")
    suspend fun getTrackingDetails(@Body body: GetTrackingDetailsRequest): TrackingDetails
  }

  @JsonClass(generateAdapter = true)
  internal data class GetTrackingDetailsRequest(
      val trackingId: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class TrackingDetails(
      val expectedDeliveryDate: Long,
      // i dont trust that these exist when they're false
      val faShipment: Boolean?,
      val reachedNearestHub: Boolean?,
      val shipmentTrackingDetails: List<TrackingEvent>,
  )

  @JsonClass(generateAdapter = true)
  internal data class TrackingEvent(
      val date: Long,
      val city: String,
      val statusDetails: String,
  )
}
