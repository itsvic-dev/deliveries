// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query

// Reverse engineered from https://foxpost.hu/csomagkovetes/
object FoxpostDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_foxpost
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  private const val BASE_URL = "https://foxpost.hu/"
  private const val USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64; rv:153.0) Gecko/20100101 Firefox/153.0"
  // Yii language cookie forcing en-US (must be URL-encoded).
  private const val LANGUAGE_COOKIE =
      "language=a076df6de21e46f81ebc9e92d4ab8c09f595c5b7db1f6644e8de02713735dc47a%3A2%3A%7Bi%3A0%3Bs%3A8%3A%22language%22%3Bi%3A1%3Bs%3A5%3A%22en-US%22%3B%7D"
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
  private val trackingIdFormat = """^CLFOX\d+$""".toRegex(RegexOption.IGNORE_CASE)

  override fun acceptsFormat(trackingId: String): Boolean {
    return trackingIdFormat.matches(trackingId)
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val response = service.track(trackingId, LANGUAGE_COOKIE)
    if (!response.isSuccessful) throw HttpException(response)

    val html = response.body()?.string() ?: throw ParcelNonExistentException()
    return parseParcelResponse(trackingId, html)
  }

  internal fun parseParcelResponse(trackingId: String, html: String): Parcel {
    val document = Jsoup.parse(html)
    val items = document.select(".parcel-status-items__list-item")
    if (items.isEmpty()) throw ParcelNonExistentException()

    val history =
        items.map { element ->
          val title =
              element.selectFirst(".parcel-status-items__list-item-title")?.text()?.trim().orEmpty()
          val description =
              element
                  .selectFirst(".parcel-status-items__list-item-description")
                  ?.text()
                  ?.trim()
                  .orEmpty()
          val date =
              element.selectFirst(".parcel-status-items__list-item-date")?.text()?.trim().orEmpty()

          ParcelHistoryItem(
              description = if (description.isEmpty()) title else "$title — $description",
              time = LocalDateTime.parse(date, dateFormatter),
              location = "",
          )
        }

    val currentItem = items.first()!!
    val currentTitle =
        currentItem.selectFirst(".parcel-status-items__list-item-title")?.text()?.trim().orEmpty()
    val currentDescription =
        currentItem
            .selectFirst(".parcel-status-items__list-item-description")
            ?.text()
            ?.trim()
            .orEmpty()

    val status =
        mapStatus(currentTitle, currentDescription) ?: logUnknownStatus("Foxpost", currentTitle)

    return Parcel(trackingId, history, status)
  }

  private fun mapStatus(title: String, description: String): Status? {
    val normalizedTitle = title.lowercase()
    val normalizedDescription = description.lowercase()

    return when {
      // Package created / Csomagod elkészült
      normalizedTitle == "package created" || normalizedTitle == "csomagod elkészült" ->
          Status.Preadvice
      // In parcel locker / Automatában — drop-off vs ready for pickup
      normalizedTitle == "in parcel locker" || normalizedTitle == "automatában" ->
          when {
            normalizedDescription.contains("ready for pickup") ||
                normalizedDescription.contains("átveheted") ||
                normalizedDescription.contains("megérkezett") -> Status.AwaitingPickup
            else -> Status.LockerboxAcceptedParcel
          }
      normalizedTitle.contains("ready for pickup") ||
          normalizedTitle.contains("átvehető") ||
          normalizedTitle.contains("átvételre vár") -> Status.AwaitingPickup
      // In warehouse / Raktárban
      normalizedTitle == "in warehouse" || normalizedTitle == "raktárban" -> Status.InWarehouse
      // En route — courier collected / in transit
      normalizedTitle == "en route" ||
          normalizedTitle.contains("courier") ||
          normalizedTitle.contains("futár") ->
          if (normalizedDescription.contains("collected") ||
              normalizedDescription.contains("courier"))
              Status.PickedUpByCourier
          else Status.InTransit
      normalizedTitle.contains("out for delivery") || normalizedTitle.contains("kiszállítás") ->
          Status.OutForDelivery
      normalizedTitle.contains("in transit") ||
          normalizedTitle.contains("on the way") ||
          normalizedTitle.contains("szállítás") ||
          normalizedTitle.contains("úton") -> Status.InTransit
      // Received / Átvéve — picked up from locker or delivered
      normalizedTitle == "received" ||
          normalizedTitle == "collected" ||
          normalizedTitle == "picked up" ||
          normalizedTitle == "átvéve" -> Status.PickedUp
      normalizedTitle.contains("delivered") || normalizedTitle.contains("kézbesítve") ->
          Status.Delivered
      // Sent back / Visszaküldve
      normalizedTitle == "sent back" || normalizedTitle == "visszaküldve" ->
          Status.ReturningToSender
      normalizedTitle.contains("returned to sender") ||
          normalizedTitle.contains("visszaérkezett") -> Status.ReturnedToSender
      else -> null
    }
  }

  private val retrofit = Retrofit.Builder().baseUrl(BASE_URL).client(api_client).build()
  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("en/parcel-tracking")
    @Headers(
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language: en-US,en;q=0.9",
        "User-Agent: $USER_AGENT",
    )
    suspend fun track(
        @Query("code") trackingId: String,
        @Header("Cookie") cookie: String,
    ): Response<ResponseBody>
  }
}
