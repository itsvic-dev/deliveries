// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.os.LocaleList
import android.text.Html
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

object GLSGlobalDeliveryService : GLSDeliveryService(R.string.service_gls, "GROUP")
object GLSHungaryDeliveryService : GLSDeliveryService(R.string.service_gls_hungary, "HU")
object GLSItalyDeliveryService : GLSDeliveryService(R.string.service_gls_italy, "IT")

open class GLSDeliveryService(
    override val nameResource: Int,
    private val region: String,
) : DeliveryService {

  // Italy: do not ask/require postal code.
  override val acceptsPostCode: Boolean = region != "IT"
  override val requiresPostCode: Boolean = region != "IT"

  private val rsttEndpoint: String =
      when (region) {
        "HU" -> "rstt029"
        "IT" -> "rstt030"
        else -> "rstt030"
      }

  override suspend fun getParcel(trackingId: String, postalCode: String?): Parcel {
    val resp: ExtendedParcelInfo =
        try {
          if (region == "IT") {
            // Italy: query-form endpoint (no postalCode), response is wrapped under tuStatus[]
            val wrapper =
                service.getExtendedParcelItaly(
                    lang = "it",
                    endpoint = rsttEndpoint,
                    match = trackingId,
                    type = "NAT",
                    caller = "witt002",
                    millis = System.currentTimeMillis(),
                )

            val first = wrapper.tuStatus.firstOrNull() ?: throw ParcelNonExistentException()
            first
          } else {
            // Other regions: existing behavior (postalCode required)
            val locale = LocaleList.getDefault().get(0).language
            val relativeUrl = "$locale/$rsttEndpoint/$trackingId"
            service.getExtendedParcel(url = relativeUrl, postalCode = postalCode!!)
          }
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
              })
        }

    val status =
        when (resp.progressBar.statusInfo) {
          "PREADVICE" -> Status.Preadvice
          "INPICKUP",
          "INTRANSIT" -> Status.InTransit
          "INWAREHOUSE" -> Status.InWarehouse
          "INDELIVERY" -> Status.OutForDelivery
          "DELIVEREDPS" -> Status.AwaitingPickup
          "DELIVERED" -> Status.Delivered
          else -> logUnknownStatus("GLS", resp.progressBar.statusInfo)
        }

    val properties = mutableMapOf<Int, String>()
    resp.infos?.forEach {
      if (it.type == "WEIGHT") properties[R.string.property_weight] = it.value
    }

    if (resp.arrivalTime != null) {
      val type =
          if (status == Status.Delivered) R.string.property_delivery_time else R.string.property_eta
      properties[type] = resp.arrivalTime.value
    }

    return Parcel(trackingId, history, status, properties)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://gls-group.com/app/service/open/rest/$region/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()
  private val service = retrofit.create(API::class.java)

  private interface API {
    // Existing behavior: /{lang}/{rsttEndpoint}/{trackingId}?postalCode=...
    @GET
    suspend fun getExtendedParcel(
        @Url url: String,
        @Query("postalCode") postalCode: String,
    ): ExtendedParcelInfo

    // Italy behavior: /it/rstt030?match=<ID>&type=NAT&caller=witt002&millis=<...>
    @GET("{lang}/{endpoint}")
    suspend fun getExtendedParcelItaly(
        @retrofit2.http.Path("lang") lang: String,
        @retrofit2.http.Path("endpoint") endpoint: String,
        @Query("match") match: String,
        @Query("type") type: String,
        @Query("caller") caller: String,
        @Query("millis") millis: Long,
    ): ItalyWrapper
  }

  @JsonClass(generateAdapter = true)
  internal data class ItalyWrapper(
      val tuStatus: List<ExtendedParcelInfo>,
  )

  @JsonClass(generateAdapter = true)
  internal data class ExtendedParcelInfo(
      val history: List<GLSHistoryItem>,
      val progressBar: Progress,
      val infos: List<GLSTypedProperty>?,
      val references: List<GLSTypedProperty>,
      val arrivalTime: GLSProperty?,
  )

  @JsonClass(generateAdapter = true)
  internal data class GLSTypedProperty(
      val type: String,
      val name: String,
      val value: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class GLSProperty(
      val name: String,
      val value: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class GLSHistoryItem(
      val time: String,
      val date: String,
      val evtDscr: String,
      val address: HistoryAddress,
  )

  @JsonClass(generateAdapter = true)
  internal data class HistoryAddress(
      val city: String,
      val countryName: String? = null, // Italy payload often omits this
  )

  @JsonClass(generateAdapter = true)
  internal data class Progress(
      val statusInfo: String,
  )
}
