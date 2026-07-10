// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import com.squareup.moshi.Moshi
import dev.itsvic.parceltracker.BuildConfig
import dev.itsvic.parceltracker.R
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.converter.moshi.MoshiConverterFactory

enum class Service {
  UNDEFINED,
  EXAMPLE,

  // International
  CAINIAO,
  DHL,
  GLS,
  UPS,
  FPX,

  // North America
  UNIUNI,

  // United Kingdom
  DPD_UK,
  EVRI,

  // Europe
  AN_POST,
  BELPOST,
  DPD_GER,
  DPD_PL,
  GLS_HUNGARY,
  HERMES,
  MAGYAR_POSTA,
  NOVA_POSHTA,
  PACKETA,
  POLISH_POST,
  POSTE_ITALIANE,
  POST_NL,
  SAMEDAY_BG,
  SAMEDAY_HU,
  SAMEDAY_RO,
  UKRPOSHTA,
  POSTNORD,
  ALLEGRO_ONEBOX,
  INPOST,
  INPOST_IT,
  ORLEN_PACZKA,

  // Asia
  EKART,
  SPX_TH,
}

val serviceOptions =
    Service.entries
        .filter {
          return@filter it != Service.UNDEFINED && it != Service.EXAMPLE
        }
        .toList()

fun getDeliveryService(service: Service): DeliveryService? {
  return when (service) {
    Service.CAINIAO -> CainiaoDeliveryService
    Service.DHL -> DhlDeliveryService
    Service.GLS -> GLSGlobalDeliveryService
    Service.UPS -> UPSDeliveryService
    Service.FPX -> FPXDeliveryService

    Service.UNIUNI -> UniUniDeliveryService

    Service.DPD_UK -> DpdUkDeliveryService
    Service.EVRI -> EvriDeliveryService

    Service.AN_POST -> AnPostDeliveryService
    Service.BELPOST -> BelpostDeliveryService
    Service.DPD_GER -> DpdGerDeliveryService
    Service.DPD_PL -> DpdPlDeliveryService
    Service.GLS_HUNGARY -> GLSHungaryDeliveryService
    Service.HERMES -> HermesDeliveryService
    Service.MAGYAR_POSTA -> MagyarPostaDeliveryService
    Service.NOVA_POSHTA -> NovaPostDeliveryService
    Service.PACKETA -> PacketaDeliveryService
    Service.POLISH_POST -> PolishPostDeliveryService
    Service.POSTE_ITALIANE -> PosteItalianeDeliveryService
    Service.POST_NL -> PostNLDeliveryService
    Service.SAMEDAY_BG -> SamedayBulgariaDeliveryService
    Service.SAMEDAY_HU -> SamedayHungaryDeliveryService
    Service.SAMEDAY_RO -> SamedayRomaniaDeliveryService
    Service.UKRPOSHTA -> UkrposhtaDeliveryService
    Service.POSTNORD -> PostNordDeliveryService
    Service.ALLEGRO_ONEBOX -> AllegroOneBoxDeliveryService
    Service.INPOST -> InPostDeliveryService
    Service.INPOST_IT -> InPostItalyDeliveryService
    Service.ORLEN_PACZKA -> OrlenPaczkaDeliveryService

    Service.EKART -> EKartDeliveryService
    Service.SPX_TH -> SPXThailandDeliveryService

    Service.EXAMPLE -> ExampleDeliveryService
    else -> null
  }
}

internal val api_client =
    OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor { Log.d("OkHttp", it) }
                .setLevel(
                    if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.BASIC))
        .build()

internal val api_moshi: Moshi = Moshi.Builder().build()
internal val api_factory = MoshiConverterFactory.create(api_moshi)

// TODO: fill out with more data
data class Parcel(
    val id: String,
    val history: List<ParcelHistoryItem>,
    val currentStatus: Status,
    val properties: Map<Int, String> = mapOf(),
)

data class ParcelHistoryItem(
    val description: String,
    val time: LocalDateTime,
    val location: String,
)

enum class Status(val nameResource: Int) {
  Preadvice(R.string.status_preadvice),
  LockerboxAcceptedParcel(R.string.status_lockerbox_accepted_parcel),
  PickedUpByCourier(R.string.status_picked_up_by_courier),
  InTransit(R.string.status_in_transit),
  Readdressed(R.string.status_readdressed),
  InWarehouse(R.string.status_in_warehouse),
  Customs(R.string.status_customs),
  CustomsSuccess(R.string.status_customs_cleared),
  CustomsHeld(R.string.status_customs_held),
  OutForDelivery(R.string.status_out_for_delivery),
  DeliveryFailure(R.string.status_delivery_failure),
  Delivered(R.string.status_delivered),
  DeliveredToNeighbor(R.string.status_delivered_to_neighbor),
  DeliveredToASafePlace(R.string.status_delivered_to_a_safe_place),
  DroppedAtCustomerService(R.string.status_dropped_at_cs),
  ReturningToSender(R.string.status_returning_to_sender),
  ReturnedToSender(R.string.status_returned_to_sender),
  AwaitingPickup(R.string.status_awaiting_pickup),
  PickupTimeEndingSoon(R.string.status_pickup_time_ending_soon),
  PickedUp(R.string.status_picked_up),
  Delayed(R.string.status_delayed),
  Damaged(R.string.status_damaged),
  Destroyed(R.string.status_destroyed),
  Unknown(R.string.status_unknown),
  NetworkFailure(R.string.status_network_failure),
  NoData(R.string.status_no_data),
}

suspend fun Context.getParcel(id: String, postCode: String?, service: Service): Parcel {
  // use DeliveryService abstraction if possible, otherwise default to the old hardcoded list
  getDeliveryService(service)?.let {
    return it.getParcel(this, id, postCode)
  }

  throw NotImplementedError("Service $service has no DeliveryService object")
}

fun getDeliveryServiceName(service: Service): Int? {
  return getDeliveryService(service)?.nameResource
}

interface DeliveryService {
  val nameResource: Int
  val acceptsPostCode: Boolean
  val requiresPostCode: Boolean
  val requiresApiKey: Boolean
    get() = false

  val apiKeyPreference: Preferences.Key<String>?
    get() = null

  suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    return TODO("DeliveryService does not implement getParcel")
  }

  suspend fun getParcel(context: Context, trackingId: String, postalCode: String?): Parcel {
    return getParcel(trackingId, postalCode)
  }

  fun acceptsFormat(trackingId: String): Boolean {
    return false
  }
}

class ParcelNonExistentException : Exception("Parcel does not exist in delivery service API")

class APIKeyMissingException :
    Exception("Delivery service requires an API key but none is present")

internal fun logUnknownStatus(service: String, data: String): Status {
  Log.d("APICore", "Unknown status reported by $service: $data")
  return Status.Unknown
}

fun localDateFromMilli(milli: Long): LocalDateTime {
  return LocalDateTime.ofInstant(Instant.ofEpochMilli(milli), TimeZone.getDefault().toZoneId())
}
