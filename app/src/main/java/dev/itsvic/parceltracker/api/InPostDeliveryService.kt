// SPDX-License-Identifier: GPL-3.0-or-later
// https://dokumentacja-inpost.atlassian.net/wiki/spaces/PL/pages/18153479 (tracking)
// https://dokumentacja-inpost.atlassian.net/wiki/spaces/PL/pages/18153478/Statuses (statuses)
package dev.itsvic.parceltracker.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

object InPostDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_inpost
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private const val BASE_URL = "https://api-shipx-pl.easypack24.net/v1/"

  private val retrofit =
      Retrofit.Builder()
          .baseUrl(BASE_URL)
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  override fun acceptsFormat(trackingId: String): Boolean {
    val inpostRegex = """^\d{24}$""".toRegex()
    return inpostRegex.matchEntire(trackingId) != null
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val response =
        try {
          service.getParcel(trackingId)
        } catch (e: HttpException) {
          if (e.code() == 400 || e.code() == 404) throw ParcelNonExistentException()
          throw e
        }

    return Parcel(
        response.trackingNumber,
        eventsToHistory(response.trackingDetails),
        statusToEnum(response.status),
    )
  }

  internal fun statusToEnum(status: String): Status =
      when (status) {
        "created",
        "offers_prepared",
        "offer_selected",
        "confirmed", -> Status.Preadvice

        "collected_from_sender",
        "taken_by_courier",
        "taken_by_courier_from_pok",
        "taken_by_courier_from_customer_service_point", -> Status.PickedUpByCourier

        "adopted_at_source_branch",
        "adopted_at_sorting_center",
        "adopted_at_target_branch",
        "stack_in_customer_service_point",
        "stack_in_box_machine", -> Status.InWarehouse

        "dispatched_by_sender",
        "dispatched_by_sender_to_pok",
        "sent_from_source_branch",
        "sent_from_sorting_center",
        "unstack_from_customer_service_point",
        "unstack_from_box_machine", -> Status.InTransit

        "out_for_delivery",
        "out_for_delivery_to_address", -> Status.OutForDelivery

        "ready_to_pickup",
        "ready_to_pickup_from_pok",
        "ready_to_pickup_from_pok_registered",
        "ready_to_pickup_from_branch",
        "avizo",
        "courier_avizo_in_customer_service_point", -> Status.AwaitingPickup

        "pickup_reminder_sent",
        "pickup_reminder_sent_address", -> Status.PickupTimeEndingSoon

        "readdressed",
        "redirect_to_box" -> Status.Readdressed

        "return_pickup_confirmation_to_sender" -> Status.Delivered
        "returned_to_sender" -> Status.ReturnedToSender
        "delay_in_delivery" -> Status.Delayed
        "delivered" -> Status.Delivered
        "other" -> Status.Unknown

        "oversized",
        "pickup_time_expired",
        "claimed",
        "rejected_by_receiver",
        "undelivered",
        "undelivered_wrong_address",
        "undelivered_incomplete_address",
        "undelivered_unknown_receiver",
        "undelivered_cod_cash_receiver",
        "undelivered_no_mailbox",
        "undelivered_not_live_address",
        "undelivered_lack_of_access_letterbox",
        "canceled",
        "canceled_redirect_to_box",
        "stack_parcel_pickup_time_expired",
        "stack_parcel_in_box_machine_pickup_time_expired",
        "missing", -> Status.DeliveryFailure

        else -> logUnknownStatus("InPost", status)
      }

  private fun eventsToHistory(details: List<TrackingDetail>): List<ParcelHistoryItem> =
      details.map { item ->
        ParcelHistoryItem(
            item.status.replace('_', ' ').replaceFirstChar { it.uppercase() },
            LocalDateTime.parse(item.datetime, DateTimeFormatter.ISO_DATE_TIME),
            item.location ?: item.agency ?: "",
        )
      }

  private interface API {
    @GET("tracking/{trackingId}")
    @Headers("Accept: application/json")
    suspend fun getParcel(@Path("trackingId") trackingId: String): TrackingResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class TrackingResponse(
      @Json(name = "tracking_number") val trackingNumber: String,
      val status: String,
      @Json(name = "tracking_details") val trackingDetails: List<TrackingDetail>,
  )

  @JsonClass(generateAdapter = true)
  internal data class TrackingDetail(
      @Json(name = "origin_status") val originStatus: String?,
      val status: String,
      val agency: String?,
      val location: String?,
      val datetime: String,
  )
}
