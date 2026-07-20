// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.os.LocaleList
import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// Reverse-engineered from https://emonitoring.poczta-polska.pl
object PolishPostDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_polish_post
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override fun acceptsFormat(trackingId: String): Boolean {
    val pocztexRegex = """^PX\d{10}$""".toRegex()
    return pocztexRegex.matchEntire(trackingId) != null || emsFormat.matchEntire(trackingId) != null
  }

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val locale = LocaleList.getDefault().get(0).language
    val req = ParcelRequest(trackingId, if (locale == "pl") "X1" else "EN", true)
    val resp =
        try {
          service.getParcel(req)
        } catch (_: Exception) {
          throw ParcelNonExistentException()
        }

    if (resp.mailInfo == null || resp.mailStatus == -1) throw ParcelNonExistentException()
    val history =
        resp.mailInfo.events.reversed().map { item ->
          ParcelHistoryItem(
              item.name,
              LocalDateTime.parse(item.time, DateTimeFormatter.ISO_DATE_TIME),
              if (item.postOffice.description != null)
                  "${item.postOffice.name}\n${item.postOffice.description.street} ${item.postOffice.description.houseNumber}\n${item.postOffice.description.zipCode} ${item.postOffice.description.city}"
              else item.postOffice.name)
        }

    val status =
        when (resp.mailInfo.events.last().code) {
          "P_NAD",
          "P_REJ_KN1" -> Status.Preadvice
          "P_ZWC" -> Status.Customs
          "P_ZWOLDDOR" -> Status.Customs
          "P_WPUCPP" -> Status.Customs
          "P_WZL" -> Status.InTransit
          "P_WD",
          "P_WDML" -> Status.OutForDelivery
          "P_D" -> Status.Delivered
          "P_A" -> Status.DeliveryFailure
          "P_KWD" -> Status.AwaitingPickup
          "P_OWU" -> Status.PickedUp
          // Post-delivery customs declaration(?)
          "P_ROZL_CEL" -> Status.Delivered
          else -> logUnknownStatus("Polish Post", resp.mailInfo.events.last().code)
        }

    return Parcel(resp.number, history, status)
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://uss.poczta-polska.pl/uss/v1.1/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private const val API_KEY =
      "BiGwVG2XHvXY+kPwJVPA8gnKchOFsyy39Thkyb1wAiWcKLQ1ICyLiCrxj1+vVGC+kQk3k0b74qkmt5/qVIzo7lTfXhfgJ72Iyzz05wH2XZI6AgXVDciX7G2jLCdoOEM6XegPsMJChiouWS2RZuf3eOXpK5RPl8Sy4pWj+b07MLg=.Mjg0Q0NFNzM0RTBERTIwOTNFOUYxNkYxMUY1NDZGMTA0NDMwQUIyRjg4REUxMjk5NDAyMkQ0N0VCNDgwNTc1NA==.b24415d1b30a456cb8ba187b34cb6a86"

  private interface API {
    @POST("tracking/checkmailex")
    @Headers("Api_key: $API_KEY")
    suspend fun getParcel(@Body data: ParcelRequest): ParcelResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class ParcelRequest(
      val number: String,
      val language: String, // X1 - Polish(?), EN - English
      val addPostOfficeInfo: Boolean,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelResponse(
      val mailInfo: MailInfo?,
      val mailStatus: Int,
      val number: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class MailInfo(
      val number: String,
      val events: List<Event>,
  )

  @JsonClass(generateAdapter = true)
  internal data class Event(
      val code: String,
      val name: String,
      val postOffice: PostOffice,
      val time: String,
  )

  @JsonClass(generateAdapter = true)
  internal data class PostOffice(
      val name: String,
      val description: PostOfficeDescription?,
  )

  @JsonClass(generateAdapter = true)
  internal data class PostOfficeDescription(
      val city: String,
      val houseNumber: String,
      val street: String,
      val zipCode: String,
  )
}
