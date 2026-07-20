package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

object CainiaoDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_cainiao
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    var userLocale = Locale.getDefault()
    var language = userLocale.language
    val country = userLocale.country

    val parcelResp = service.getParcel(trackingId, "$language-$country", "$language-$country")

    if (!parcelResp.success ||
        parcelResp.module.isEmpty() ||
        parcelResp.module.first().detailList.isEmpty()) {
      throw ParcelNonExistentException()
    }

    val parcel = parcelResp.module.first()

    val history =
        parcel.detailList.map {
          ParcelHistoryItem(
              it.standerdDesc,
              // Sometimes new parcels have no timezone info, so default to origin country (china)
              LocalDateTime.ofInstant(
                  Instant.ofEpochMilli(it.time),
                  ZoneId.of(if (it.timeZone.isBlank()) "GMT+8" else it.timeZone)),
              "")
        }

    // Based on the few tracking numbers I have, there may be more
    val status =
        when (parcel.detailList.first().actionCode) {
          "GWMS_ACCEPT" -> Status.Preadvice
          "GWMS_PACKAGE" -> Status.AwaitingPickup
          "GWMS_OUTBOUND" -> Status.InTransit
          "PU_PICKUP_SUCCESS" -> Status.PickedUp
          "CW_OUTBOUND" -> Status.InTransit
          "SC_INBOUND_SUCCESS" -> Status.InWarehouse
          "SC_OUTBOUND_SUCCESS" -> Status.InTransit
          "LH_HO_IN_SUCCESS" -> Status.InWarehouse
          "LH_HO_AIRLINE" -> Status.InTransit
          "LH_DEPART" -> Status.InTransit
          "LH_ARRIVE" -> Status.InWarehouse
          "CC_EX_START" -> Status.Customs
          "CC_EX_SUCCESS" -> Status.CustomsSuccess
          "CC_HO_IN_SUCCESS" -> Status.Customs
          "CC_HO_OUT_SUCCESS" -> Status.InTransit
          "CC_IM_START" -> Status.Customs
          "CC_IM_SUCCESS" -> Status.CustomsSuccess
          "TD_TRANSWH_OUTBOUND" -> Status.InTransit
          "GTMS_ACCEPT" -> Status.InTransit
          "GTMS_DO_ARRIVE" -> Status.InWarehouse
          "GTMS_DO_DEPART" -> Status.OutForDelivery
          "GTMS_STATION_OUT" -> Status.InTransit
          "GTMS_SIGNED" -> Status.Delivered
          "GTMS_DEL_FAILURE" -> Status.DeliveryFailure
          "GTMS_STA_SIGNED" -> Status.AwaitingPickup
          "TD_TRANS_ARRIVE_DCP" -> Status.InTransit
          "TD_TRANS_DEPART_C" -> Status.InTransit
          "TD_TRANS_ARRIVE_C" -> Status.InTransit
          "PU_PRE_PICKUP_SUCCESS" -> Status.PickedUpByCourier
          "PRE_READY_TO_SHIP" -> Status.Preadvice
          "GSTA_INBOUND" -> Status.AwaitingPickup
          "LH_BIGBAG_SHELVE" -> Status.InWarehouse
          "COMMON_INTRANSIT" -> Status.InTransit
          else -> logUnknownStatus("Cainiao", parcel.detailList.first().actionCode)
        }

    return Parcel(trackingId, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://global.cainiao.com/global/")
          .client(api_client)
          .addConverterFactory(MoshiConverterFactory.create(api_moshi))
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("detail.json")
    suspend fun getParcel(
        @Query("mailNos") trackingID: String,
        @Query("lang") lang: String,
        @Query("language") language: String
    ): ParcelResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class ParcelResponse(
      val module: List<CainiaoParcel>,
      val success: Boolean,
  )

  @JsonClass(generateAdapter = true)
  internal data class CainiaoParcel(
      val detailList: List<Event>,
  )

  @JsonClass(generateAdapter = true)
  internal data class Event(
      val actionCode: String,
      val standerdDesc: String,
      val time: Long,
      val timeZone: String,
  )
}
