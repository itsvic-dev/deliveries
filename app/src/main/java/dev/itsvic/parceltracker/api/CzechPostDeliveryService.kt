// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.os.LocaleList
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

object CzechPostDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_czech_post
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override fun acceptsFormat(trackingId: String): Boolean {
    // Example: DR7049601914C, RR123456789CZ
    return emsFormat.accepts(trackingId) || """^[A-Z]{2}\d{9,11}[A-Z0-9]?$""".toRegex().accepts(trackingId)
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val locale = LocaleList.getDefault().get(0).language
    val lang = if (locale == "cs") "cs" else "en"

    val resp = try {
      service.getDataAsJson(trackingId, lang)
    } catch (e: HttpException) {
      if (e.code() == 404) throw ParcelNonExistentException()
      else throw e
    }

    val parcelData = resp.firstOrNull() ?: throw ParcelNonExistentException()
    val statesList = parcelData.states?.state ?: emptyList()

    val history = statesList.reversed().map {
      ParcelHistoryItem(
        it.text,
        parseDate(it.date),
        if (it.postoffice != null) "${it.postcode ?: ""} ${it.postoffice}".trim() else ""
      )
    }

    val latestState = statesList.lastOrNull()
    val status = when (latestState?.idIcon) {
      1 -> Status.Preadvice
      2, 3 -> Status.InTransit
      4, 5 -> Status.InTransit // 4: Arrived at depot, 5: Departure from depot
      8 -> Status.Delivered
      13 -> Status.OutForDelivery
      14 -> Status.Delivered
      15 -> Status.AwaitingPickup
      16 -> Status.DeliveryFailure
      21 -> Status.Customs
      else -> {
        // Fallback to text matching if icon is unknown
        val text = latestState?.text?.lowercase() ?: ""
        when {
          text.contains("dodáno") || text.contains("delivered") -> Status.Delivered
          text.contains("uloženo") || text.contains("stored") -> Status.AwaitingPickup
          text.contains("doručování") || text.contains("delivery") -> Status.OutForDelivery
          text.contains("podána") || text.contains("posted") -> Status.Preadvice
          else -> Status.InTransit
        }
      }
    }

    return Parcel(parcelData.id ?: trackingId, history, status)
  }

  private fun parseDate(dateStr: String): LocalDateTime {
    return try {
      if (dateStr.length == 10) {
        java.time.LocalDate.parse(dateStr).atStartOfDay()
      } else {
        LocalDateTime.parse(dateStr.replace(" ", "T"), DateTimeFormatter.ISO_DATE_TIME)
      }
    } catch (e: Exception) {
      LocalDateTime.now()
    }
  }

  private val retrofit = Retrofit.Builder()
    .baseUrl("https://b2c.cpost.cz/services/ParcelHistory/")
    .client(api_client)
    .addConverterFactory(api_factory)
    .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("getDataAsJson")
    suspend fun getDataAsJson(
      @Query("idParcel") id: String,
      @Query("language") language: String
    ): List<CzechPostParcel>
  }

  @JsonClass(generateAdapter = true)
  internal data class CzechPostParcel(
    val id: String?,
    val states: CzechPostStates?
  )

  @JsonClass(generateAdapter = true)
  internal data class CzechPostStates(
    val state: List<CzechPostState>?
  )

  @JsonClass(generateAdapter = true)
  internal data class CzechPostState(
    val id: String?,
    val date: String,
    val text: String,
    val postcode: String?,
    val postoffice: String?,
    val idIcon: Int?
  )
}
