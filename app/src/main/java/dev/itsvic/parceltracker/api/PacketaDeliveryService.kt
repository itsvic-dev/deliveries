// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.os.LocaleList
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okio.IOException
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.POST
import retrofit2.http.Path

// reverse engineered from their Nuxt app lol
object PacketaDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_packeta
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override fun acceptsFormat(trackingId: String): Boolean {
    return """^Z\s?\d{3}\s?\d{4}\s?\d{3}$""".toRegex().matchEntire(trackingId) != null
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val cleanId = trackingId.replace(Regex("\\s"), "").lowercase().replace("z", "")
    val locale = LocaleList.getDefault().get(0)

    val resp =
        try {
          service.getPacketById(cleanId, locale.language)
        } catch (e: HttpException) {
          if (e.code() == 400 || e.code() == 404) throw ParcelNonExistentException()
          else throw IOException()
        }

    val history =
        resp.item.trackingDetails.reversed().map {
          ParcelHistoryItem(
              it.text,
              LocalDateTime.parse(it.time.replace(' ', 'T'), DateTimeFormatter.ISO_DATE_TIME),
              // location is not its own field, and regex is unreliable
              // as addresses are just tacked on the end with inconsistent formatting
              "")
        }

    val status =
        when (resp.item.packetStatusId) {
          // guesswork from the enum found in packeta web tracker code
          "1" -> Status.InWarehouse // WAITING_FOR_DELIVERY
          "2" -> Status.AwaitingPickup // READY_FOR_PICKUP
          "3" -> Status.Delivered // ISSUED_AND_ACCOUNTED
          "21" -> Status.Unknown // LOST_OR_UNKNOWN
          "31" -> Status.InTransit // ON_THE_WAY
          "997" -> Status.InTransit // TO_BE_PROCESSED
          else -> logUnknownStatus("Packeta", resp.item.packetStatusId)
        }

    return Parcel(resp.item.barcode, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://tracking.packeta.com/api/")
          .client(api_client)
          .addConverterFactory(MoshiConverterFactory.create(api_moshi))
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @POST("getPacketById/{id}/{locale}")
    suspend fun getPacketById(
        @Path("id") id: String,
        @Path("locale") locale: String
    ): PacketResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class PacketResponse(
      val item: Packet,
  )

  @JsonClass(generateAdapter = true)
  internal data class Packet(
      val barcode: String,
      val packetStatusId: String,
      val trackingDetails: List<Event>,
  )

  @JsonClass(generateAdapter = true)
  internal data class Event(
      val text: String,
      val time: String,
  )
}
