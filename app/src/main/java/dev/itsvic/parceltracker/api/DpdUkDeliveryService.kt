// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.util.Log
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

object DpdUkDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_dpd_uk
  override val acceptsPostCode: Boolean = true
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val urlResp = service.getParcelURL(trackingId, postCode)

    if (urlResp.code() == 404) throw ParcelNonExistentException()
    Log.d("DpdUk", "$urlResp")
    val id = urlResp.raw().request.url.pathSegments.last()
    Log.d("DpdUk", "id=$id")

    val events = service.getParcelEvents(id)

    val history =
        events.data.map {
          ParcelHistoryItem(
              it.eventText,
              LocalDateTime.parse(it.eventDate.replace(' ', 'T'), DateTimeFormatter.ISO_DATE_TIME),
              it.eventLocation,
          )
        }

    val status =
        when (val statusCode = events.data.first().eventCode) {
          "000" -> Status.Preadvice
          "009" -> Status.InTransit
          "004" -> Status.InWarehouse
          "015" -> Status.OutForDelivery
          "001" -> Status.Delivered
          null -> Status.Unknown
          else -> logUnknownStatus("DPD UK", statusCode)
        }

    return Parcel(trackingId, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://apis.track.dpd.co.uk/v1/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("track")
    suspend fun getParcelURL(
        @Query("parcel") trackingId: String,
        @Query("postcode") postcode: String?
    ): Response<ResponseBody>

    // currently unused by us
    // @GET("parcels/{id}")
    // suspend fun getParcelInfo(@Path("id") id: String)

    @GET("parcels/{id}/parcelevents")
    suspend fun getParcelEvents(@Path("id") id: String): ParcelEventsResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class ParcelEventsResponse(val data: List<Event>)

  @JsonClass(generateAdapter = true)
  internal data class Event(
      val eventCode: String?,
      val eventDate: String,
      val eventText: String,
      val eventLocation: String,
  )
}
