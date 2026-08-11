// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AllegroSession(
    val username: String,
    val accessToken: String,
    val accessTokenExpiration: String? = null,
    val wdctx: String,
    val datadome: String,
    val qxlsessid: String? = null,
)

data class AllegroPackage(
    val packageId: String,
    val title: String,
    val status: String,
    val carrier: String = "",
    val trackingNumber: String = "",
    val eta: String = "",
    val pickupPoint: String = "",
    val readyForPickup: Boolean = false,
    val carrierId: String = "",
    val pickupCode: String = "",
    val pickupPhoneNumber: String = "",
    val multiboxGroupId: String = "",
    val multiboxIndex: String = "",
    val history: List<AllegroTrackingEvent> = emptyList(),
)

data class AllegroTrackingEvent(val status: String, val timestamp: String = "")

data class AllegroPickupDetails(
    val waybill: String,
    val carrierId: String,
    val carrierName: String = "",
    val code: String = "",
    val phoneNumber: String = "",
    val qrPayload: String = "",
)

open class AllegroException(message: String, val statusCode: Int? = null) : Exception(message)

open class AllegroAuthenticationException(message: String, statusCode: Int? = null) :
    AllegroException(message, statusCode)

class AllegroSessionExpiredException(message: String, statusCode: Int? = null) :
    AllegroAuthenticationException(message, statusCode)
