package dev.itsvic.parceltracker.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dev.itsvic.parceltracker.R
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

// Reverse engineered from https://www.orlenpaczka.pl/sledz-paczke
object OrlenPaczkaDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_orlen_paczka
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private const val BASE_URL = "https://nadaj.orlenpaczka.pl/"

  // Orlen Paczka's API returns a JSONP instead of JSON so we need a custom Converter to remove the
  // callback
  class JsonpConverterFactory(private val moshi: Moshi) : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
      if (type != ParcelResponse::class.java) return null

      return Converter { responseBody ->
        val jsonp = responseBody.string()
        val json = jsonp.substringAfter("callback(").substringBefore(")")
        moshi.adapter(ParcelResponse::class.java).fromJson(json)
      }
    }

    companion object {
      fun create(moshi: Moshi): JsonpConverterFactory {
        return JsonpConverterFactory(moshi)
      }
    }
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl(BASE_URL)
          .client(api_client)
          .addConverterFactory(JsonpConverterFactory.create(Moshi.Builder().build()))
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  override fun acceptsFormat(trackingId: String): Boolean {
    val orlenPaczkaRegex = """^[0-9]{14}$""".toRegex()
    return orlenPaczkaRegex.matchEntire(trackingId) != null
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val response =
        try {
          service.getParcel(trackingId, "callback", Instant.now().toEpochMilli())
        } catch (_: Exception) {
          throw ParcelNonExistentException()
        }

    if (response.status.isEmpty()) {
      throw ParcelNonExistentException()
    }

    return Parcel(
        trackingId, convertHistory(response.history), mapCodeToStatus(response.history.last().code))
  }

  private fun mapCodeToStatus(code: String): Status {
    return when (code) {
      "200" -> Status.Preadvice
      "210" -> Status.LockerboxAcceptedParcel
      "240" -> Status.PickedUpByCourier
      "100" -> Status.InWarehouse
      "653" -> Status.InWarehouse
      "680" -> Status.OutForDelivery
      "690" -> Status.AwaitingPickup
      "1000" -> Status.Delivered
      else -> logUnknownStatus("Orlen Paczka", code)
    }
  }

  private fun convertHistory(history: List<HistoryEntry>): List<ParcelHistoryItem> {
    return history.reversed().map { item ->
      ParcelHistoryItem(
          item.label,
          LocalDateTime.parse(item.date, DateTimeFormatter.ofPattern("dd-MM-yyyy, HH:mm")),
          "" // the API doesn't provide any location fields
          )
    }
  }

  private interface API {
    @GET("parcel/api-status")
    suspend fun getParcel(
        @Query("id") parcelId: String,
        @Query("jsonp") jsonp: String,
        @Query("_") currentMillis: Long
    ): ParcelResponse
  }

  @JsonClass(generateAdapter = true)
  data class ParcelResponse(
      val status: String,
      val number: String,
      val full: Boolean,
      val historyHtml: String,
      val history: List<HistoryEntry>,
      val label: String,
      @Json(name = "return") val returnField: Boolean,
      val truckNo: String,
      val returnTruck: String,
  )

  @JsonClass(generateAdapter = true)
  data class HistoryEntry(
      val date: String,
      val code: String,
      val label: String,
      val labelShort: String,
  )
}
