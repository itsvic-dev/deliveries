package dev.itsvic.parceltracker.api

import android.os.LocaleList
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

// Reverse-engineered from https://www.postnord.com/track-and-trace/
// And additional information about locales from
// https://developer.postnord.com/apis/details?systemName=shipment-v5-trackandtrace-shipmentinformation
object PostNordDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_postnord
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  // Tracking URL parsing not yet supported. PostNord's widely-used shareable format
  // is tracking.postnord.com's widget link (https://tracking.postnord.com/?id=
  // <obfuscated>), documented in PostNord's own tracking widget implementation
  // guide - but that id is an opaque mix of an API key and the shipment ID, not a
  // recoverable tracking number, so it can't be parsed as-is. Per-country sites
  // (postnord.se/.no/.dk/.com) may accept a plain shipmentId query param when
  // tracking manually, but that's unconfirmed.

  // Define supported locales and default
  private const val DEFAULT_LOCALE = "en"
  private val supportedLocales = setOf("en", "sv", "no", "da", "fi")

  private fun getApiLocale(): String {
    val locale = LocaleList.getDefault().get(0).language
    return if (supportedLocales.contains(locale)) locale else DEFAULT_LOCALE
  }

  private val statusMapping =
      mapOf(
          "CREATED" to Status.Preadvice,
          "EN_ROUTE" to Status.InTransit,
          "DELAYED" to Status.InTransit,
          "EXPECTED_DELAY" to Status.InTransit,
          "AVAILABLE_FOR_DELIVERY" to Status.InWarehouse,
          "AVAILABLE_FOR_DELIVERY_PAR_LOC" to Status.InWarehouse,
          "DELIVERED" to Status.Delivered,
          "DELIVERY_IMPOSSIBLE" to Status.DeliveryFailure,
          "DELIVERY_REFUSED" to Status.DeliveryFailure,
          "STOPPED" to Status.DeliveryFailure,
          "RETURNED" to Status.DeliveryFailure,
          "OTHER" to Status.Unknown,
          "INFORMED" to Status.Unknown,
      )

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val resp =
        try {
          service.getShipments(trackingId, getApiLocale())
        } catch (e: HttpException) {
          when (e.code()) {
            404 -> throw ParcelNonExistentException()
            else -> throw IOException("Failed to fetch parcel information: ${e.message()}")
          }
        }

    val item = resp.items.firstOrNull() ?: throw ParcelNonExistentException()

    val status = statusMapping[item.status.code] ?: logUnknownStatus("PostNord", item.status.code)

    val history =
        item.events.map { event ->
          val location = event.location
          val locationName = location.name
          val locationCountryCode = location.countryCode

          ParcelHistoryItem(
              event.eventDescription,
              ZonedDateTime.parse(event.eventTime)
                  .withZoneSameInstant(ZoneId.systemDefault())
                  .toLocalDateTime(),
              listOfNotNull(locationName, locationCountryCode).joinToString(", "),
          )
        }

    return Parcel(resp.shipmentId, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://api2.postnord.com/rest/shipment/v1/trackingweb/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("shipmentInformation")
    @Headers("x-bap-key: web-tracking-sc")
    suspend fun getShipments(
        @Query("shipmentId") id: String,
        @Query("locale") locale: String,
    ): ShipmentResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class ShipmentResponse(val shipmentId: String, val items: List<Item>)

  @JsonClass(generateAdapter = true)
  internal data class Item(
      val itemId: String,
      val deliveryInformation: DeliveryInformation,
      val events: List<Event>,
      val status: ItemStatus,
  )

  @JsonClass(generateAdapter = true)
  internal data class DeliveryInformation(val deliveryTo: String, val deliveryToInfo: String)

  @JsonClass(generateAdapter = true)
  internal data class Event(
      val eventDescription: String,
      val eventTime: String, // ISO-8601 representation of the datetime
      val status: String,
      val location: Location,
  )

  @JsonClass(generateAdapter = true)
  internal data class Location(
      val countryCode: String? = null,
      val locationType: String?,
      val name: String?,
  )

  @JsonClass(generateAdapter = true)
  internal data class ItemStatus(val code: String, val header: String, val description: String)
}
