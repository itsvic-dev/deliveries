// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.olx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class OlxSession(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String?,
    val expiresAtEpochMillis: Long,
    val accountId: String,
    val displayName: String,
)

data class OlxAuthorizationRequest(
    val authorizationUrl: String,
    val codeVerifier: String,
    val state: String,
)

@Serializable
internal data class OlxTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 1200,
)

@Serializable internal data class OlxDataResponse<T>(val data: T)

@Serializable
internal data class OlxProfileResponse(
    val data: OlxProfileDto,
)

@Serializable
internal data class OlxProfileDto(
    val id: Long? = null,
    val uuid: String? = null,
    val name: String? = null,
    val email: String? = null,
)

@Serializable
internal data class OlxOrderOverviewDto(
    val entries: List<OlxOrderDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
internal data class OlxOrderDto(
    val id: String,
    val shortId: String? = null,
    val createdAt: String? = null,
    val status: OlxOrderStatusDto? = null,
    val items: OlxOrderItemsDto = OlxOrderItemsDto(),
    val actions: List<OlxOrderActionDto> = emptyList(),
    val fulfillment: OlxOrderFulfillmentDto? = null,
)

@Serializable
internal data class OlxOrderStatusDto(
    val name: String,
    val tags: List<String> = emptyList(),
    val type: String? = null,
    val deadline: OlxOrderDeadlineDto? = null,
)

@Serializable internal data class OlxOrderDeadlineDto(val expiresAt: String? = null)

@Serializable
internal data class OlxOrderItemsDto(
    val entries: List<OlxOrderItemEntryDto> = emptyList(),
)

@Serializable
internal data class OlxOrderItemEntryDto(
    val item: OlxOrderItemDto,
    val quantity: Int = 1,
)

@Serializable
internal data class OlxOrderItemDto(
    val id: String,
    val title: String,
)

@Serializable
internal data class OlxOrderActionDto(
    val name: String,
    val subjectId: String? = null,
    val url: String? = null,
)

@Serializable
internal data class OlxOrderFulfillmentDto(
    val product: OlxOrderDeliveryDto? = null,
)

@Serializable
internal data class OlxOrderDeliveryDto(
    val option: OlxOrderDeliveryOptionDto? = null,
    val method: String? = null,
    val shortName: String? = null,
)

@Serializable
internal data class OlxOrderDeliveryOptionDto(
    val name: String? = null,
    val fallback: String? = null,
)

internal class OlxException(message: String, val status: Int? = null) : Exception(message)

internal class OlxSessionExpiredException(message: String, val status: Int? = null) :
    Exception(message)
