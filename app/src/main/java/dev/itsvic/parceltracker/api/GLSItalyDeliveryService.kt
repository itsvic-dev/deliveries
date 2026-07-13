// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.text.Html
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

object GLSItalyDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_gls_italy
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://gls-group.com/app/service/open/rest/IT/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val resp: GlsItExtendedParcelInfo =
        try {
          val wrapper =
              service.getExtendedParcelItaly(
                  lang = "it",
                  endpoint = "rstt030",
                  match = trackingId,
                  type = "NAT",
                  caller = "witt002",
                  millis = System.currentTimeMillis(),
              )
          wrapper.tuStatus.firstOrNull() ?: throw ParcelNonExistentException()
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    val history =
        resp.history.map { item ->
          ParcelHistoryItem(
              Html.fromHtml(item.evtDscr, Html.FROM_HTML_MODE_LEGACY).toString(),
              LocalDateTime.parse("${item.date}T${item.time}", DateTimeFormatter.ISO_DATE_TIME),
              when {
                item.address.countryName == null && item.address.city.isNotEmpty() -> item.address.city
                item.address.countryName != null && item.address.city.isNotEmpty() ->
                    "${item.address.city}, ${item.address.countryName}"
                item.address.countryName != null && item.address.city.isEmpty() -> item.address.countryName
                else -> ""
              },
          )
        }

    val status =
        when (resp.progressBar.statusInfo) {
          "PREADVICE" -> Status.Preadvice
          "INPICKUP", "INTRANSIT" -> Status.InTransit
          "INWAREHOUSE" -> Status.InWarehouse
          "INDELIVERY" -> Status.OutForDelivery
          "DELIVEREDPS" -> Status.AwaitingPickup
          "DELIVERED" -> Status.Delivered
          else -> logUnknownStatus("GLS Italy", resp.progressBar.statusInfo)
        }

    val properties = mutableMapOf<Int, String>()
    resp.infos?.forEach {
      if (it.type == "WEIGHT") properties[R.string.property_weight] = it.value
    }

    resp.arrivalTime?.let {
      val type =
          if (status == Status.Delivered) R.string.property_delivery_time else R.string.property_eta
      properties[type] = it.value
    }

    return Parcel(trackingId, history, status, properties)
  }

  private interface API {
    @GET("{lang}/{endpoint}")
    suspend fun getExtendedParcelItaly(
        @Path("lang") lang: String,
        @Path("endpoint") endpoint: String,
        @Query("match") match: String,
        @Query("type") type: String,
        @Query("caller") caller: String,
        @Query("millis") millis: Long,
    ): GlsItWrapper
  }

  @JsonClass(generateAdapter = true)
  internal data class GlsItWrapper(
      val tuStatus: List<GlsItExtendedParcelInfo>,
  )

  @JsonClass(generateAdapter = true)
  internal data class GlsItExtendedParcelInfo(
      val history: List<GlsItHistoryItem>,
      val progressBar: GlsItProgress,
      val infos: List<GlsItTypedProperty>?,
      val references: List<GlsItTypedProperty>,
      val arrivalTime: GlsItProperty?,
  )

  @JsonClass(generateAdapter = true)
  internal data class GlsItTypedProperty(
      val type: String,
      val name: String,
      val value: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class GlsItProperty(
      val name: String,
      val value: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class GlsItHistoryItem(
      val time: String,
      val date: String,
      val evtDscr: String,
      val address: GlsItHistoryAddress,
  )

  @JsonClass(generateAdapter = true)
  internal data class GlsItHistoryAddress(
      val city: String,
      val countryName: String? = null,
  )

  @JsonClass(generateAdapter = true)
  internal data class GlsItProgress(
      val statusInfo: String,
  )
}
