package dev.itsvic.parceltracker.api

import com.squareup.moshi.JsonClass
import dev.itsvic.parceltracker.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

object UniUniDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_uniuni
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    val key = getRequestKey()
    if (key == null) {
      // TODO: use a proper exception here
      throw ParcelNonExistentException()
    }

    val resp =
        try {
          service.getParcelInfo(trackingId, key)
        } catch (_: HttpException) {
          throw ParcelNonExistentException()
        }

    if (resp.data.valid_tno.isEmpty()) {
      throw ParcelNonExistentException()
    }

    val parcel = resp.data.valid_tno.first()

    val history =
        parcel.spath_list.reversed().map {
          ParcelHistoryItem(it.pathInfo, localDateFromMilli(it.pathTime * 1000), it.pathAddress)
        }

    // https://docs.uniuni.com/#d0cdf4e9-53d8-4a09-b4cb-db2187d36877
    val status =
        when (parcel.state) {
          190,
          223 -> Status.Preadvice
          192,
          198 -> Status.Customs
          199,
          200,
          218,
          221,
          229 -> Status.InWarehouse
          195,
          204,
          217,
          225,
          255 -> Status.InTransit
          202 -> Status.OutForDelivery
          203,
          228 -> Status.Delivered
          206,
          207,
          209,
          211,
          212,
          213,
          215,
          222,
          224,
          230,
          231,
          232 -> Status.DeliveryFailure
          214,
          226 -> Status.AwaitingPickup
          216 -> Status.PickedUp
          else -> logUnknownStatus("UniUni", parcel.state.toString())
        }

    return Parcel(parcel.tno, history, status)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun getRequestKey(): String? {
    val keyRegex = """(?<=&key=)[^"]*""".toRegex()
    val request = Request.Builder().url("https://www.uniuni.com/tracking/").build()

    api_client.newCall(request).executeAsync().use { response ->
      val result = keyRegex.find(response.body.string())
      if (result == null) return null
      return result.value
    }
  }

  private val retrofit =
      Retrofit.Builder()
          .baseUrl("https://delivery-api.uniuni.ca/cargo/")
          .client(api_client)
          .addConverterFactory(api_factory)
          .build()

  private val service = retrofit.create(API::class.java)

  private interface API {
    @GET("trackinguniuninew")
    suspend fun getParcelInfo(
        @Query("id") trackingId: String,
        @Query("key") apiKey: String,
    ): GetParcelResponse
  }

  @JsonClass(generateAdapter = true)
  internal data class GetParcelResponse(val data: GetParcelResponseData)

  @JsonClass(generateAdapter = true)
  internal data class GetParcelResponseData(
      val valid_tno: List<ParcelInfo>,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelInfo(
      val tno: String,
      val state: Int,
      val spath_list: List<ParcelEvent>,
  )

  @JsonClass(generateAdapter = true)
  internal data class ParcelEvent(
      val pathTime: Long,
      val pathInfo: String,
      val pathAddress: String,
  )
}
