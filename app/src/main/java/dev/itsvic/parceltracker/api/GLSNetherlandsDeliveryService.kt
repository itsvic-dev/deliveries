// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.os.LocaleList
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path

// gls-group.eu doesn't have tracking data for parcels handled by GLS Netherlands; gls-info.nl
// does, using an entirely different, reverse-engineered private API.
object GLSNetherlandsDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_gls_netherlands
  override val acceptsPostCode: Boolean = true
  override val requiresPostCode: Boolean = true

  override val trackingUrlPatterns: List<TrackingUrlPattern> =
      listOf(
          TrackingUrlPattern(
              urlRegex =
                  """https?://(?:www\.)?gls-info\.nl/tracking/ttlink\?\S*parcelNo=([A-Za-z0-9]+)"""
                      .toRegex(RegexOption.IGNORE_CASE),
              postalCodeRegex = """[?&]zipCode=([A-Za-z0-9]+)""".toRegex(RegexOption.IGNORE_CASE),
          ))

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    // the site only ships nl and en translations; anything else falls back to English
    val culture = if (LocaleList.getDefault().get(0).language == "nl") "nl-NL" else "en-GB"
    val postalCode = postCode!!.replace(" ", "").uppercase()

    val resp =
        try {
          service.getParcelDetails(id = trackingId, postalCode = postalCode, locale = culture)
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    val history =
        resp.scans.reversed().map {
          ParcelHistoryItem(
              it.eventReasonDescr,
              LocalDateTime.parse(it.dateTime),
              when {
                it.depotName != null && it.depotName != "-" && it.countryName != null ->
                    "${it.depotName}, ${it.countryName}"
                it.depotName != null && it.depotName != "-" -> it.depotName
                it.countryName != null -> it.countryName
                else -> ""
              })
        }

    val status =
        when (resp.state) {
          State.ANNOUNCED -> Status.Preadvice
          State.RECEIVED -> Status.PickedUpByCourier
          State.IN_REGION -> Status.InWarehouse
          State.OUT_FOR_DELIVERY -> Status.OutForDelivery
          State.DELIVERED ->
              when (resp.subState) {
                SubState.DELIVERED_AT_NEIGHBORS -> Status.DeliveredToNeighbor
                SubState.DELIVERED_AT_PARCEL_SHOP,
                SubState.DELIVERED_AT_APL -> Status.AwaitingPickup
                SubState.COLLECTED_FROM_PARCEL_SHOP,
                SubState.COLLECTED_FROM_APL,
                SubState.PICKUP_COLLECTED -> Status.PickedUp
                else -> Status.Delivered
              }
          State.NOT_DELIVERED -> Status.DeliveryFailure
          State.RETURN ->
              if (resp.subState == SubState.RETURNED_TO_SENDER) Status.ReturnedToSender
              else Status.ReturningToSender
          State.IN_LOCKER -> Status.AwaitingPickup
          else ->
              logUnknownStatus("GLS Netherlands", "state=${resp.state} subState=${resp.subState}")
        }

    val properties = mutableMapOf<Int, String>()

    (resp.weighedWeight ?: resp.suppliedWeight)?.let {
      properties[R.string.property_weight] = "$it kg"
    }

    val deliveryTime = resp.deliveryScanInfo?.dateTime
    val eta = resp.deliveryStatus?.etaTimestamp
    if (status == Status.Delivered && deliveryTime != null) {
      properties[R.string.property_delivery_time] =
          LocalDateTime.parse(deliveryTime)
              .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
    } else if (eta != null) {
      properties[R.string.property_eta] =
          LocalDateTime.parse(eta).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
    }

    return Parcel(trackingId, history, status, properties)
  }

  // https://apm.gls.nl parcel state/sub-state enums, taken from gls-info.nl's frontend bundle
  private object State {
    const val ANNOUNCED = 0
    const val RECEIVED = 1
    const val IN_REGION = 2
    const val OUT_FOR_DELIVERY = 3
    const val DELIVERED = 4
    const val NOT_DELIVERED = 5
    const val RETURN = 6
    const val IN_LOCKER = 7
  }

  private object SubState {
    const val DELIVERED_AT_NEIGHBORS = 2
    const val DELIVERED_AT_PARCEL_SHOP = 3
    const val RETURNED_TO_SENDER = 6
    const val DELIVERED_AT_APL = 7
    const val COLLECTED_FROM_APL = 9
    const val COLLECTED_FROM_PARCEL_SHOP = 10
    const val PICKUP_COLLECTED = 14
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://apm.gls.nl/api/tracktrace/v1/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()
  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("{id}/postalcode/{postalCode}/details/{locale}")
    suspend fun getParcelDetails(
        @Path("id") id: String,
        @Path("postalCode") postalCode: String,
        @Path("locale") locale: String,
    ): ParcelDetails
  }

  @JsonClass(generateAdapter = true)
  internal data class ParcelDetails(
      val state: Int,
      val subState: Int,
      val scans: List<Scan>,
      val weighedWeight: Double?,
      val suppliedWeight: Double?,
      val deliveryStatus: DeliveryStatus?,
      val deliveryScanInfo: DeliveryScanInfo?,
  )

  @JsonClass(generateAdapter = true)
  internal data class Scan(
      val dateTime: String,
      val eventReasonDescr: String,
      val depotName: String?,
      val countryName: String?,
  )

  @JsonClass(generateAdapter = true) internal data class DeliveryStatus(val etaTimestamp: String?)

  @JsonClass(generateAdapter = true)
  internal data class DeliveryScanInfo(val isDelivered: Boolean, val dateTime: String?)
}
