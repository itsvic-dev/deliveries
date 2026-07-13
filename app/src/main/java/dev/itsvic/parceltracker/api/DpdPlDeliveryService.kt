// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import okhttp3.Cookie
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

// Reverse engineered from https://tracktrace.dpd.com.pl
object DpdPlDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_dpd_pl
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private const val BASE_URL = "https://tracktrace.dpd.com.pl/"
  private const val USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64; rv:152.0) Gecko/20100101 Firefox/152.0"
  private val trackingIdFormat = """^(?:\d{14}|\d{13}[A-Z])$""".toRegex(RegexOption.IGNORE_CASE)

  override fun acceptsFormat(trackingId: String): Boolean {
    return trackingIdFormat.matches(trackingId)
  }

  override suspend fun getParcel(trackingId: String, postalCode: String?): Parcel {
    // The form endpoint requires a session established by parcelDetails and rejects OkHttp's
    // default user agent.
    val session = service.prepareSession()
    if (!session.isSuccessful) throw HttpException(session)

    val cookies =
        Cookie.parseAll(session.raw().request.url, session.headers()).joinToString("; ") {
          "${it.name}=${it.value}"
        }
    val referer = session.raw().request.url.toString()
    session.body()?.close()

    if (cookies.isEmpty()) error("DPD Poland did not issue a session cookie")

    val response =
        service
            .findPackage(
                trackingId = trackingId,
                type = 1,
                cookies = cookies,
                referer = referer,
            )
            .string()
    return parseParcelResponse(trackingId, response)
  }

  internal fun parseParcelResponse(trackingId: String, html: String): Parcel {
    val rows = Jsoup.parseBodyFragment(html).select("table.table-track tbody > tr")
    if (rows.isEmpty()) throw ParcelNonExistentException()

    val history =
        rows
            .map { row ->
              val cells = row.children().filter { it.tagName() == "td" }.map { it.text() }
              require(cells.size == 4) { "Unexpected DPD Poland history row" }

              ParcelHistoryItem(
                  description = cells[2],
                  time = LocalDateTime.parse("${cells[0]}T${cells[1]}"),
                  location = cells[3],
              )
            }
            .sortedByDescending { it.time }

    val currentEvent =
        history.firstOrNull { !it.description.startsWith("Powiadomienie ", ignoreCase = true) }
    val status =
        currentEvent?.let {
          mapStatus(it.description) ?: logUnknownStatus("DPD Poland", it.description)
        } ?: Status.Unknown
    return Parcel(trackingId, history, status)
  }

  private fun mapStatus(description: String): Status? {
    val normalized = description.lowercase()
    return when {
      normalized.startsWith("zarejestrowano dane przesyłki") -> Status.Preadvice
      normalized.startsWith("przesyłka odebrana przez kuriera") -> Status.PickedUpByCourier
      normalized.startsWith("przyjęcie w oddziale dpd") ||
          normalized.startsWith("przyjęcie przesyłki w oddziale dpd") ||
          normalized.startsWith("przyjęcie paczki w sortowni") -> Status.InWarehouse
      normalized.startsWith("przekazano za granicę") -> Status.InTransit
      normalized.startsWith("przeadresowanie przesyłki") -> Status.Readdressed
      normalized.startsWith("wydanie do doręczenia") ||
          normalized.startsWith("wydanie przesyłki do doręczenia") -> Status.OutForDelivery
      normalized.startsWith("przesyłka doręczona") -> Status.Delivered
      else -> null
    }
  }

  private val retrofit = Retrofit.Builder().baseUrl(BASE_URL).client(api_client).build()
  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("parcelDetails")
    @Headers(
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language: pl,en-US;q=0.7,en;q=0.3",
        "User-Agent: $USER_AGENT",
    )
    suspend fun prepareSession(): Response<ResponseBody>

    @FormUrlEncoded
    @POST("findPackage")
    @Headers(
        "Accept: text/html, */*; q=0.01",
        "Accept-Language: pl,en-US;q=0.7,en;q=0.3",
        "Origin: https://tracktrace.dpd.com.pl",
        "User-Agent: $USER_AGENT",
        "X-Requested-With: XMLHttpRequest",
    )
    suspend fun findPackage(
        @Field("q") trackingId: String,
        @Field("typ") type: Int,
        @Header("Cookie") cookies: String,
        @Header("Referer") referer: String,
    ): ResponseBody
  }
}
