// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

object SpeedXDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_spx
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private const val BASE_URL = "https://tracking.speedx.io/"

  override fun acceptsFormat(trackingId: String): Boolean {
    return TRACKING_CODE_REGEX.matches(trackingId)
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val response =
        try {
          service.getParcel(TrackRequest(listOf(trackingId), UUID.randomUUID().toString()))
        } catch (_: Exception) {
          throw ParcelNonExistentException()
        }

    val events = response.filter { it.eventCode != null }.map { it.toTrackEvent() }
    if (events.isEmpty()) throw ParcelNonExistentException()

    // The public /api/tracks endpoint only returns the most recent event per package.
    // Enrich with the full timeline embedded in the website's server-rendered payload.
    val historyEvents = fetchFullHistory(trackingId).filter { it.eventCode != null }
    val sorted = sortEvents(historyEvents.takeIf { it.isNotEmpty() } ?: events)

    val history =
        sorted.map {
          ParcelHistoryItem(
              listOfNotNull(it.eventDescription, it.eventSupplementalInfo).joinToString(" "),
              parseLocalTs(it.localTs),
              it.location ?: "")
        }

    return Parcel(trackingId, history, mapStatus(sorted.first().eventCode!!))
  }

  internal fun sortEvents(events: List<TrackEvent>): List<TrackEvent> {
    return events.sortedByDescending { it.localTs?.let(::parseLocalTs) ?: LocalDateTime.now() }
  }

  internal fun extractEvents(flight: String): List<TrackEvent>? {
    val marker = "\"events\":["
    val markerIndex = flight.indexOf(marker)
    if (markerIndex < 0) return null
    val raw = extractJsonArray(flight, markerIndex + marker.length - 1) ?: return null
    return runCatching { trackEventsAdapter.fromJson("{\"events\":$raw}")?.events }.getOrNull()
  }

  internal fun mapStatus(eventCode: String): Status {
    return when {
      eventCode == "50001" -> Status.Preadvice // Shipping Label Created
      eventCode == "50002" || eventCode == "57112" ->
          Status.InTransit // In transit to SpeedX / destination
      eventCode == "57104" -> Status.OutForDelivery // Out for Delivery
      eventCode == "57201" || eventCode == "57202" || eventCode == "57203" -> Status.Delivered
      eventCode == "56004" -> Status.CustomsSuccess // Customs clearance
      eventCode == "57411" -> Status.Delayed // Bad weather
      eventCode == "57607" || eventCode == "59116" ->
          Status.DeliveryFailure // Attempted / undelivered
      eventCode.startsWith("52") -> Status.InWarehouse // Origin handling
      eventCode.startsWith("56") -> Status.Customs
      eventCode == "57101" || eventCode == "57102" -> Status.InWarehouse // At destination facility
      else -> Status.InTransit
    }
  }

  private fun extractJsonArray(text: String, openIndex: Int): String? {
    var depth = 0
    var inString = false
    var escaped = false
    var i = openIndex
    while (i < text.length) {
      val c = text[i]
      if (inString) {
        if (escaped) {
          escaped = false
        } else if (c == '\\') {
          escaped = true
        } else if (c == '"') {
          inString = false
        }
      } else {
        when (c) {
          '"' -> inString = true
          '[' -> depth += 1
          ']' -> {
            depth -= 1
            if (depth == 0) return text.substring(openIndex, i + 1)
          }
        }
      }
      i += 1
    }
    return null
  }

  private suspend fun fetchFullHistory(trackingId: String): List<TrackEvent> {
    val request = Request.Builder().url("$BASE_URL$trackingId").addHeader("RSC", "1").build()
    return try {
      api_client.newCall(request).executeAsync().use { response ->
        if (!response.isSuccessful) {
          emptyList()
        } else {
          val body = response.body.string()
          extractEvents(body) ?: emptyList()
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun parseLocalTs(localTs: String?): LocalDateTime {
    return localTs
        ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        ?.atZoneSameInstant(ZoneId.systemDefault())
        ?.toLocalDateTime() ?: LocalDateTime.now()
  }

  private fun TrackResponse.toTrackEvent(): TrackEvent {
    return TrackEvent(eventCode, eventDescription, eventSupplementalInfo, location, localTs)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl(BASE_URL)
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private val trackEventsAdapter: JsonAdapter<TrackEventList> =
      api_moshi.adapter(TrackEventList::class.java)

  private interface API {
    @POST("api/tracks") suspend fun getParcel(@Body request: TrackRequest): List<TrackResponse>
  }

  @JsonClass(generateAdapter = true)
  internal data class TrackRequest(val trackingNumbers: List<String>, val visitorId: String)

  @JsonClass(generateAdapter = true)
  internal data class TrackResponse(
      val eventCode: String? = null,
      val eventDescription: String? = null,
      val eventSupplementalInfo: String? = null,
      val location: String? = null,
      val localTs: String? = null,
  )

  @JsonClass(generateAdapter = true)
  internal data class TrackEventList(val events: List<TrackEvent>)

  @JsonClass(generateAdapter = true)
  internal data class TrackEvent(
      val eventCode: String? = null,
      val eventDescription: String? = null,
      val eventSupplementalInfo: String? = null,
      val location: String? = null,
      val localTs: String? = null,
  )

  private val TRACKING_CODE_REGEX = Regex("""^SPX[A-Z]{2,6}\d{9,20}$""", RegexOption.IGNORE_CASE)
}
