// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

// Poste Italiane (Italian Post)
// fucking asshats with their shitty italian API

object PosteItalianeDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_poste_italiane
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val resp =
        try {
          service.getParcel(GetParcelRequest(trackingId))
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    if (resp.listaMovimenti.isNullOrEmpty()) {
      throw ParcelNonExistentException()
    }

    val events =
        resp.listaMovimenti.reversed().map {
          ParcelHistoryItem(
              it.statoLavorazione,
              LocalDateTime.ofInstant(
                  Instant.ofEpochMilli(it.dataOra), TimeZone.getDefault().toZoneId()),
              it.luogo,
          )
        }

    val status =
        when (resp.listaMovimenti.last().box) {
          "3" -> Status.InTransit
          "4" -> Status.DeliveryFailure
          "5" -> Status.Delivered
          else -> logUnknownStatus("Italian Post", resp.listaMovimenti.last().box)
        }

    return Parcel(trackingId, events, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://www.poste.it/online/dovequando/DQ-REST/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @POST("ricercasemplice") suspend fun getParcel(@Body data: GetParcelRequest): GetParcelResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class GetParcelRequest(
      val codiceSpedizione: String,
      val tipoRichiedente: String = "WEB",
      val periodoRicerca: Int = 1,
  )

  @JsonClass(generateAdapter = true)
  internal data class GetParcelResponse(
      /** movement list */
      val listaMovimenti: List<ParcelEvent>?,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelEvent(
      /** possibly state of the event? */
      val box: String,
      /** event date, epoch with milliseconds */
      val dataOra: Long,
      /** return flag */
      val flagRitorno: Boolean,
      /** post code */
      val frazionario: String?,
      /** place */
      val luogo: String,
      /** state in italian */
      val statoLavorazione: String,
  )
}
