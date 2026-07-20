// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

object BelpostDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_belpost
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val req = ParcelRequest(trackingId)
    val resp =
        try {
          service.getParcel(req)
        } catch (_: Exception) {
          throw ParcelNonExistentException()
        }

    if (resp.data.isEmpty()) {
      throw ParcelNonExistentException()
    }

    val history =
        resp.data[0].steps.map { item ->
          ParcelHistoryItem(
              item.event,
              LocalDateTime.ofInstant(
                  Instant.ofEpochSecond(item.timestamp.toLong()), TimeZone.getDefault().toZoneId()),
              item.place)
        }

    /*
    1 - accepted from sender (outside country)
    3 - arrived to international warehouse (customs?, outside country)
    4 - accepted from sender
    6 - processing
    8 - sent (outside country)
    15 - sent
    21 - picked up
    30 - arrived to international warehouse (customs?, inside country)
    31 - ready for customs
    32 - arrived to warehouse (related to customs)
    35 - directed (customs)
    38 - end of customs
    70 - arrived to warehouse
     */
    val status =
        if (resp.data[0].steps.isEmpty()) Status.Preadvice
        else
            when (resp.data[0].steps.first().code.toInt()) {
              1 -> Status.Preadvice
              3 -> Status.Customs
              4 -> Status.Preadvice
              6 -> Status.InWarehouse
              8 -> Status.InTransit
              15 -> Status.InTransit
              21 -> Status.OutForDelivery
              30 -> Status.Customs
              31 -> Status.Customs
              32 -> Status.InWarehouse
              35 -> Status.InTransit
              38 -> Status.Customs
              70 -> Status.InWarehouse
              else -> logUnknownStatus("Belpost", resp.data[0].steps.last().code.toString())
            }

    return Parcel(resp.data[0].number, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://api.belpost.by/api/v1/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @POST("tracking") suspend fun getParcel(@Body data: ParcelRequest): ParcelResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class ParcelRequest(
      val number: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelResponse(
      // not sure why this is a list when it always has one item
      val data: List<MailInfo>,
      val uuid: String,
      val barcode: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class MailInfo(
      val number: String,
      val steps: List<Event>,
  )

  @JsonClass(generateAdapter = true)
  internal data class Event(
      val code: Double,
      val event: String,
      val place: String,
      val timestamp: Double,
  )
}
