package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.misc.Rfc3339LocalDateTimeJsonAdapter
import java.time.LocalDateTime
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

object PostNLDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_postnl
  override val acceptsPostCode: Boolean = true
  override val requiresPostCode: Boolean = true

  // Confirmed: verified against real link
  override val trackingUrlPatterns: List<TrackingUrlPattern> =
      listOf(
          TrackingUrlPattern(
              urlRegex =
                  """https?://jouw\.postnl\.nl/track-and-trace/([A-Za-z0-9]+)-"""
                      .toRegex(RegexOption.IGNORE_CASE),
              postalCodeRegex =
                  """track-and-trace/[A-Za-z0-9]+-[A-Za-z]{2}-([A-Za-z0-9]+)"""
                      .toRegex(RegexOption.IGNORE_CASE),
          ))

  private val retrofit: Retrofit

  init {
    val apiMoshi: Moshi =
        Moshi.Builder().add(LocalDateTime::class.java, Rfc3339LocalDateTimeJsonAdapter()).build()
    val apiFactory = MoshiConverterFactory.create(apiMoshi)

    this.retrofit =
        Retrofit.Builder()
            .baseUrl("https://jouw.postnl.nl/track-and-trace/api/")
            .client(api_client)
            .addConverterFactory(apiFactory)
            .build()
  }

  private val service = retrofit.create(API::class.java)

  override suspend fun getParcel(
      trackingId: String,
      postCode: String?,
  ): dev.itsvic.parceltracker.api.Parcel {
    val resp =
        try {
          // TODO: support deliveries to countries other than Netherlands
          service.getParcel("${trackingId}-NL-${postCode}")
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    val parcel = resp.colli[trackingId] ?: throw ParcelNonExistentException()

    var etaHistoryItem: ParcelHistoryItem? = null
    if (parcel.eta != null && parcel.eta.start != null && parcel.eta.end != null) {
      etaHistoryItem =
          ParcelHistoryItem(
              "Package Expected between ${parcel.eta.start} ${parcel.eta.end}",
              parcel.eta.start,
              "",
          )
    }

    val observations =
        parcel.analyticsInfo.allObservations.sortedByDescending { it.observationDate }

    val observationsHistoryItems =
        observations
            .filter { (_, _, description) -> description != null }
            .map { (observationDate, observationCode, description) ->
              ParcelHistoryItem(description ?: "", observationDate, location = "")
            }

    val currentStatus =
        observations.firstOrNull { it.status() != Status.Unknown }?.status() ?: Status.Unknown

    val history = (etaHistoryItem?.let { listOf(it) } ?: emptyList()) + observationsHistoryItems
    return Parcel(trackingId, history = history, currentStatus)
  }

  private interface API {
    @GET("trackAndTrace/{parcel}")
    suspend fun getParcel(
        @Path("parcel") parcel: String,
        @Query("language") lang: String = "en",
    ): GetParcelResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class GetParcelResponse(val colli: Map<String, Parcel>)

  @JsonClass(generateAdapter = true)
  internal data class Parcel(
      val analyticsInfo: AnalyticsInfo,
      val observations: List<Observation>,
      val eta: Eta?,
  ) {
    @JsonClass(generateAdapter = true)
    internal data class AnalyticsInfo(val allObservations: List<Observation>)

    @JsonClass(generateAdapter = true)
    internal data class Eta(
        val type: String,
        val start: LocalDateTime?,
        val end: LocalDateTime?,
    )

    @JsonClass(generateAdapter = true)
    internal data class Observation(
        val observationDate: LocalDateTime,
        val observationCode: String,
        val description: String?,
    ) {

      fun status(): Status {
        return eventCodeToStatus(this.observationCode)
      }
    }
  }

  // from: https://developer.postnl.nl/docs/#/http/reference-data/t-t-status-codes/event-codes
  private fun eventCodeToStatus(eventCode: String): Status {
    return when (eventCode) {
      "A01",
      "A10",
      "A71",
      "F01",
      "J93",
      "M03",
      "X80",
      "X81" -> Status.Preadvice // "01, Zending voorgemeld"
      "B01",
      "X40" -> Status.InTransit // "02, Zending in ontvangst genomen"
      "B04" -> Status.PickedUpByCourier // "03, Zending afgehaald"
      "Z80" -> Status.PickedUp
      "D02",
      "D03",
      "D04",
      "D05",
      "D06",
      "D07",
      "D09",
      "D40",
      "D41",
      "H31",
      "X42",
      "X43",
      "Y80",
      "Y81" -> Status.Unknown // "04, Zending niet afgehaald"
      "A08",
      "A09",
      "A11",
      "A12",
      "A13",
      "A27",
      "A28",
      "A29",
      "A30",
      "A31",
      "A32",
      "A34",
      "A35",
      "A42",
      "A43",
      "A99",
      "J01",
      "J06",
      "J07",
      "J09",
      "J10",
      "J14",
      "J16",
      "J17",
      "J22",
      "J24",
      "J26",
      "J27",
      "J28",
      "J33",
      "J36",
      "J61",
      "J90",
      "Q12",
      "Q13",
      "R01",
      "R06",
      "X01",
      "X02",
      "X07",
      "X08",
      "X11",
      "X14",
      "X17",
      "X20",
      "X21",
      "X22",
      "X50",
      "X51",
      "X68",
      "Y23",
      "Y24",
      "Y33",
      "Y35",
      "Y60",
      "Y61",
      "Y62" -> Status.InTransit // "05, Zending gesorteerd"
      "D08",
      "H10",
      "K01",
      "K02",
      "K03",
      "K30",
      "K92",
      "V01",
      "V06",
      "Y07" -> Status.InWarehouse // "06, Zending niet gesorteerd"
      "H32",
      "H35",
      "J05",
      "J08",
      "J29",
      "J39",
      "J41",
      "J42",
      "J45",
      "J47",
      "J48",
      "J55",
      "J94",
      "J95",
      "K14",
      "K15",
      "K70",
      "Q15",
      "S05",
      "T02",
      "X04",
      "X06",
      "X19",
      "X55",
      "Y05" -> Status.InTransit // "07, Zending in distributieproces"
      "H01",
      "H02",
      "H03",
      "H04",
      "H05",
      "H07",
      "H08",
      "H09",
      "H12",
      "H20",
      "H22",
      "H24",
      "H34",
      "H36",
      "H38",
      "H51",
      "H52",
      "J92",
      "K16",
      "K41",
      "K43",
      "Y01",
      "Y02",
      "Y03",
      "Y04",
      "Y08",
      "Y09",
      "Y10",
      "Y11",
      "Y12",
      "Y13",
      "Y14",
      "Y15",
      "Y16",
      "Y17",
      "Y18",
      "Y19",
      "Y20",
      "Y21",
      "Y22",
      "Y30",
      "Y31",
      "Y32",
      "Y36",
      "Y38",
      "Y39",
      "Y40",
      "Y41",
      "Y42",
      "Y43",
      "Y44",
      "Y50",
      "Y71" -> Status.DeliveryFailure // "08, Zending niet afgeleverd"
      "J59",
      "X30",
      "X60",
      "X61",
      "X62",
      "X63",
      "X64",
      "X65",
      "X66",
      "Y25",
      "Y29" -> Status.CustomsHeld // "09, Zending bij douane"
      "I01",
      "I03",
      "I05",
      "I06",
      "I07",
      "I09",
      "I10",
      "I11",
      "I12",
      "I22",
      "I23",
      "J63",
      "J91",
      "K91",
      "X24",
      "Y26",
      "Z01",
      "Z02",
      "Z03",
      "Z04",
      "Z05",
      "Z06",
      "Z08",
      "Z09" -> Status.Delivered // "11, Zending afgeleverd"
      "I08",
      "J02",
      "J12",
      "J23",
      "J52",
      "X05" -> Status.AwaitingPickup // "12, Zending beschikbaar op afhaallocatie"
      "A20",
      "A21",
      "A22",
      "Q02",
      "X03" -> Status.Preadvice // "13, Voorgemeld: nog niet aangenomen"
      "M01" -> Status.Preadvice // "14, Voorgemeld: definitief niet aangenomen"
      "G03" -> Status.PickedUpByCourier // "15, Manco collectie"
      "G01",
      "G02",
      "G05" -> Status.InWarehouse // "16, Manco sortering"
      "H23",
      "K90" -> Status.OutForDelivery // "17, Manco distributie"
      "F03",
      "F04",
      "F05",
      "F06",
      "F07",
      "F08",
      "F10" -> Status.Destroyed // "19, Zending afgekeurd"
      "A76",
      "X10",
      "X67" -> Status.Customs // "20, Zending in inklaringsproces"
      "V02",
      "V03",
      "V04",
      "V11",
      "V12",
      "V13",
      "V21",
      "V22",
      "V23",
      "V31",
      "V50" -> Status.InWarehouse // "21, Zending in voorraad"
      "I02" -> Status.PickedUp // "22, Zending afgehaald van Postkantoor"
      "C01" -> Status.Preadvice // "23, Afhaalopdracht gecollecteerd"
      "H16",
      "H25" -> Status.ReturningToSender // "27, Retour Onbestelbaar"
      "Y37" -> Status.ReturningToSender // "28, Retour Foutieve aflevercode"
      "A72",
      "A74",
      "A75",
      "X15" -> Status.CustomsSuccess // "31, Zending klaar voor transport naar land van bestemming"
      "A41",
      "A46",
      "A73",
      "A94",
      "B06",
      "C02",
      "H15",
      "H17",
      "H18",
      "H30",
      "J56",
      "J80",
      "K25",
      "K71",
      "P20",
      "P21",
      "P22",
      "P23",
      "P24",
      "Q14",
      "Q16",
      "Q17",
      "Q18",
      "Q19",
      "X16",
      "X52",
      "Y45",
      "Y46",
      "Y47",
      "Y48",
      "Y64",
      "Y65",
      "Y66" -> Status.Unknown // "99, Niet van toepassing"'
      else -> logUnknownStatus("PostNL", "unknown event code: $eventCode")
    }
  }
}
