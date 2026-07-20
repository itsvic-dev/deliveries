// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

object AnPostDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_an_post
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override fun acceptsFormat(trackingId: String): Boolean {
    val anPostParcelFormat = """^[A-Z]{2}\d{9}IE$""".toRegex(RegexOption.IGNORE_CASE)
    return anPostParcelFormat.matchEntire(trackingId) != null
  }

  // Public API key for request header
  private val apiKey = "01b0162771e941639046a97936f72e95"

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://apim-anpost-apwebapis.anpost.com/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @POST("ttservice-public-apweb/GetEvents")
    suspend fun getEvents(
        @Header("Ocp-Apim-Subscription-Key") key: String,
        @Body request: GetEventsRequest
    ): GetEventsResponse

    @POST("ttservice-public-apweb/GetItemSummary")
    suspend fun getItemSummary(
        @Header("Ocp-Apim-Subscription-Key") key: String,
        @Body request: GetItemSummaryRequest
    ): GetItemSummaryResponse
  }

  @JsonClass(generateAdapter = true) data class GetEventsRequest(val getEvents: GetEvents)

  @JsonClass(generateAdapter = true) data class GetEvents(val barcodeItem: String)

  @JsonClass(generateAdapter = true)
  data class GetEventsResponse(val getEventsResponse: GetEventsResponseData)

  @JsonClass(generateAdapter = true)
  data class GetEventsResponseData(val GetEventsResult: List<GetEventsResult>)

  @JsonClass(generateAdapter = true)
  data class GetEventsResult(
      val activity: String,
      val date: String,
      val location: String,
      val reason: String,
      val traceCode: Int
  )

  @JsonClass(generateAdapter = true)
  data class GetItemSummaryRequest(val getItemSummary: GetItemSummary)

  @JsonClass(generateAdapter = true) data class GetItemSummary(val trackingItems: List<String>)

  @JsonClass(generateAdapter = true)
  data class GetItemSummaryResponse(val getItemSummaryResponse: GetItemSummaryResponseData)

  @JsonClass(generateAdapter = true)
  data class GetItemSummaryResponseData(val GetItemSummaryResult: List<GetItemSummaryResult>)

  @JsonClass(generateAdapter = true)
  data class GetItemSummaryResult(
      val anPostNo: String,
      val countryOfOrigin: String,
      val date: String,
      val deliveryRecordFlag: Boolean,
      val geisDeliveryName: String,
      val geisDeliveryOffice: String,
      val location: String,
      val reason: String,
      val receiverName: String,
      val senderNo: String,
      val status: String
  )

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val resp =
        try {
          service.getEvents(apiKey, GetEventsRequest(GetEvents(trackingId)))
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    if (resp.getEventsResponse.GetEventsResult.isEmpty()) {
      throw ParcelNonExistentException()
    }

    val events =
        resp.getEventsResponse.GetEventsResult.map {
          ParcelHistoryItem(
              it.activity,
              LocalDateTime.parse(it.date, DateTimeFormatter.ISO_DATE_TIME),
              it.location)
        }

    val status = mapTraceCode(resp.getEventsResponse.GetEventsResult.first().traceCode)

    return Parcel(trackingId, events, status)
  }

  // There is a lot of trace codes so I did my best to categorise them
  private fun mapTraceCode(traceCode: Int): Status {
    return when (traceCode) {
      35 -> Status.Preadvice // "Your item is on its way to us"
      15 -> Status.InWarehouse // "Your item has been handed to An Post"
      48 -> Status.InWarehouse // "We have your item and will process it for delivery"
      1 -> Status.InTransit // "Your delivery has been sorted in"
      52 -> Status.InTransit // "Your delivery is in"
      73 -> Status.InTransit // "Your item is being prepared for delivery"
      4 -> Status.OutForDelivery // "Your item is out for delivery"
      13 -> Status.DeliveryFailure // "We tried to deliver your item"
      14 -> Status.Delivered // "Your item has been delivered"
      else -> logUnknownStatus("AnPost", traceCode.toString())
    }
  }
}
