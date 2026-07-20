// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.Instant
import java.time.ZoneId
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

object EvriDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_evri
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val urnResp =
        try {
          val resp = service.getParcelURN(trackingId)
          if (resp.parcelIdentifiers.isEmpty()) throw ParcelNonExistentException()

          resp
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    val urn = urnResp.parcelIdentifiers.first().urn
    val parcelResp = service.getParcel(urn)
    val parcel = parcelResp.results.first()

    val history =
        parcel.trackingEvents.map {
          ParcelHistoryItem(
              it.trackingPoint.description,
              Instant.parse(it.dateTime).atZone(ZoneId.systemDefault()).toLocalDateTime(),
              "")
        }

    val status =
        when (parcel.trackingEvents.first().trackingStage.trackingStageCode) {
          "1" -> Status.Preadvice
          "2" -> Status.InWarehouse
          "3" -> Status.InTransit
          "4_COURIER" -> Status.OutForDelivery
          "5_COURIER" -> Status.Delivered
          else ->
              logUnknownStatus(
                  "Evri", parcel.trackingEvents.first().trackingStage.trackingStageCode)
        }

    return Parcel(trackingId, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://api.hermesworld.co.uk/enterprise-tracking-api/v1/")
          .client(api_client)
          .addConverterFactory(MoshiConverterFactory.create(api_moshi))
          .build()

  private val service = retrofit.create(API::class.java)

  // TODO: there may be more info with a postcode, but i can't really test that
  private interface API {
    @Headers("Apikey: 0DExZiK9in2ihGce7cDPrnpQ4s4nIpWG")
    @GET("parcels/reference/{id}")
    suspend fun getParcelURN(@Path("id") trackingId: String): ParcelReferenceResponse

    @Headers("Apikey: Vi8HZURvXHANfpiFDGta6bJclafLJcAY")
    @GET("parcels")
    suspend fun getParcel(@Query("uniqueIds") urn: String): ParcelResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class ParcelReferenceResponse(
      val parcelIdentifiers: List<ParcelIdentifier>,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelIdentifier(
      val urn: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelResponse(
      val failures: List<FailedQueries>,
      val results: List<EvriParcel>,
  )

  @JsonClass(generateAdapter = true)
  internal data class FailedQueries(
      val uniqueId: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class EvriParcel(
      val trackingEvents: List<Event>,
  )

  @JsonClass(generateAdapter = true)
  internal data class Event(
      val trackingPoint: TrackingPoint,
      val trackingStage: TrackingStage,
      val dateTime: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class TrackingPoint(
      val description: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class TrackingStage(
      val trackingStageCode: String,
  )
}
