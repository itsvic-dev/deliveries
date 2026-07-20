// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

object SPXThailandDeliveryService :
    SPXDeliveryService(
        "https://spx.co.th/api/v2/",
        "MGViZmZmZTYzZDJhNDgxY2Y1N2ZlN2Q1ZWJkYzlmZDY=",
        R.string.service_spx_th)

open class SPXDeliveryService(
    baseURL: String,
    private val magicValue: String,
    override val nameResource: Int
) : DeliveryService {
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val timestamp = (System.currentTimeMillis() / 1000).toString()
    val checksum =
        MessageDigest.getInstance("SHA-256")
            .digest("$trackingId$timestamp$magicValue".toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }

    val slsTrackingNumber = "$trackingId|$timestamp$checksum"
    val resp =
        try {
          service.getParcel(slsTrackingNumber)
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    if (resp.data == null) throw ParcelNonExistentException()

    val history =
        resp.data.tracking_list.map {
          ParcelHistoryItem(
              // shit patch for their shit bug
              it.message.replace("\\\\n", ""),
              LocalDateTime.ofInstant(
                  Instant.ofEpochMilli(it.timestamp * 1000L), TimeZone.getDefault().toZoneId()),
              "")
        }

    val status =
        when (resp.data.current_status) {
          "Created" -> Status.Preadvice
          "Pending_Receive" -> Status.InTransit
          "Pending" -> Status.InWarehouse
          "Assigned" -> Status.OutForDelivery
          "Delivered" -> Status.Delivered
          else -> logUnknownStatus("SPX", resp.data.current_status)
        }

    return Parcel(resp.data.sls_tracking_number, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl(baseURL)
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("fleet_order/tracking/search")
    suspend fun getParcel(
        @Query("sls_tracking_number") trackingNumber: String,
        @Header("x-language") language: String = "en",
    ): GetParcelResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class GetParcelResponse(
      val data: ParcelData?,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelData(
      val current_status: String,
      val sls_tracking_number: String,
      val tracking_list: List<ParcelTrackingEvent>,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelTrackingEvent(
      val message: String,
      val timestamp: Long,
  )
}
