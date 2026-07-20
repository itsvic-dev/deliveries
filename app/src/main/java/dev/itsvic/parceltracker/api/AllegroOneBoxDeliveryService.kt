package dev.itsvic.parceltracker.api

import android.os.LocaleList
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// Reverse-engineered from https://allegro.pl/kampania/one/kurier/sledzenie-paczki
// and https://allegro.pl/moje-allegro/zakupy/kupione/:transationId
object AllegroOneBoxDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_allegro_onebox
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private const val BASE_URL = "https://edge.allegro.pl/"

  private val retrofit =
      Retrofit.Builder()
          .baseUrl(BASE_URL)
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  override fun acceptsFormat(trackingId: String): Boolean {
    val allegroOneBoxRegex = """^A[A-Z0-9]{9}$""".toRegex()
    return allegroOneBoxRegex.matchEntire(trackingId) != null
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val language = mapLanguageToAPIFormat(LocaleList.getDefault().get(0).language)
    val response =
        try {
          service.getParcel(trackingId, language)
        } catch (_: Exception) {
          throw ParcelNonExistentException()
        }

    if (response.status.isEmpty()) {
      throw ParcelNonExistentException()
    }

    return Parcel(
        trackingId,
        eventsToHistory(response.status),
        descriptionToStatus(language, response.status.first().description))
  }

  private fun mapLanguageToAPIFormat(language: String): String {
    return when (language) {
      "pl" -> "pl-PL"
      else -> "en-US" // Fallback to English
    }
  }

  // A dirty hack to obtain a Status value.
  // The API does not provide event codes but only descriptions.
  // We can consider accessing "logistics/carriers/ALLEGRO/waybills/:trackingId/history" endpoint
  // but it requires authentication
  private fun descriptionToStatus(apiLanguage: String, description: String): Status {
    // FIXME: Find all possible statuses. Provided descriptions seem to vary despite the same
    // underlying message 🤷🏻‍♂️
    return when (apiLanguage) {
      "pl-PL" ->
          when (description) {
            "Przesyłka została przygotowana przez nadawcę" -> Status.Preadvice
            "Przesyłka została nadana" -> Status.InTransit
            "Przesyłka została podjęta z punktu przez kuriera" -> Status.InTransit
            "Przesyłka wyjechała w drogę do punktu docelowego" -> Status.InTransit
            "Przesyłka została przyjęta w oddziale" -> Status.InWarehouse
            "Przesyłka została wydana do doręczenia" -> Status.OutForDelivery
            "Przesyłka oczekuje na odbiór" -> Status.AwaitingPickup
            "Przesyłka została doręczona" -> Status.PickedUp
            "Przesyłka nie została doręczona i wróciła do oddziału" -> Status.DeliveryFailure
            "Przesyłka została odebrana przez kuriera" -> Status.InTransit
            "Kurier przekazał przesyłkę do magazynu" -> Status.InWarehouse
            "Przesyłka została nadana w punkcie" -> Status.InTransit
            else -> Status.Unknown
          }
      "en-US" ->
          when (description) {
            "Shipment has been prepared by the sender" -> Status.Preadvice
            "Shipment has been dispatched" -> Status.InTransit
            "Shipment has been picked up from parcel locker by courier" -> Status.InTransit
            "Shipment has been dispatched to its destination point" -> Status.InTransit
            "Shipment has been picked up by the courier" -> Status.InTransit
            "Shipment has been accepted at the delivery center" -> Status.InWarehouse
            "Shipment has been released for delivery" -> Status.OutForDelivery
            "Shipment is awaiting pick-up" -> Status.AwaitingPickup
            "Shipment has been delivered" -> Status.Delivered
            "Shipment has not been delivered and has returned to the delivery center" ->
                Status.DeliveryFailure
            "Shipment has been picked up from point by courier" -> Status.InTransit
            "Courier has transferred the shipment to the warehouse" -> Status.InWarehouse
            "Shipment has been dispatched at point" -> Status.InTransit
            else -> Status.Unknown
          }
      else -> Status.Unknown
    }
  }

  private fun eventsToHistory(events: List<Event>): List<ParcelHistoryItem> {
    return events.map { item ->
      ParcelHistoryItem(
          item.description,
          LocalDateTime.ofInstant(
              Instant.parse(item.eventTimestamp), TimeZone.getDefault().toZoneId()),
          "" // the API doesn't provide us any locations :(
          )
    }
  }

  private interface API {
    @GET("one/tracking")
    suspend fun getParcel(
        @Query("packageNo") packageNo: String,
        @Header("Accept-Language") language: String
    ): ParcelResponse
  }

  @JsonClass(generateAdapter = true) internal data class ParcelResponse(val status: List<Event>)

  @JsonClass(generateAdapter = true)
  internal data class Event(
      val eventTimestamp: String,
      val description: String,
  )
}
