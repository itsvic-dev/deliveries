// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.olx

import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.Status
import java.util.Locale
import kotlinx.serialization.json.Json

internal object OlxParser {
  private val json = Json { ignoreUnknownKeys = true }

  fun deliveryService(carrierId: String?, carrierName: String?): Service? {
    val carrier = listOfNotNull(carrierId, carrierName).joinToString(" ").uppercase(Locale.ROOT)
    return when {
      "INPOST" in carrier -> Service.INPOST
      "DPD" in carrier -> Service.DPD_PL
      "POCZTA" in carrier || "POCZTEX" in carrier -> Service.POLISH_POST
      "ORLEN" in carrier -> Service.ORLEN_PACZKA
      "DHL" in carrier -> Service.DHL
      "GLS" in carrier -> Service.GLS
      "UPS" in carrier -> Service.UPS
      "PACKETA" in carrier -> Service.PACKETA
      else -> null
    }
  }

  fun parsePurchases(payload: String): OlxOrderOverviewDto =
      json.decodeFromString<OlxDataResponse<OlxOrderOverviewDto>>(payload).data

  fun trackingNumber(order: OlxOrderDto): String? =
      order.actions
          .firstOrNull { it.name == "TRACK_DELIVERY" }
          ?.subjectId
          ?.takeIf(String::isNotBlank)

  fun trackingUrl(order: OlxOrderDto): String? =
      order.actions.firstOrNull { it.name == "TRACK_DELIVERY" }?.url

  fun title(order: OlxOrderDto, fallback: String): String {
    val titles = order.items.entries.map { it.item.title.trim() }.filter(String::isNotBlank)
    return when {
      titles.isEmpty() -> fallback
      titles.size == 1 -> titles.first()
      else -> "${titles.first()} + ${titles.size - 1}"
    }
  }

  fun statusToAppStatus(status: String): Status {
    val value = status.uppercase(Locale.ROOT)
    return when {
      value.contains("PICKED_UP") -> Status.PickedUp
      value.contains("READY_FOR_PICKUP") || value.contains("AWAITING_PICKUP") ->
          Status.AwaitingPickup
      value.contains("DELIVERED") || value.contains("PAYOUT") || value.contains("COMPLETED") ->
          Status.Delivered
      value.contains("RETURNED") -> Status.ReturnedToSender
      value.contains("RETURN") -> Status.ReturningToSender
      value.contains("EXPIRED") || value.contains("FAILED") || value.contains("CANCEL") ->
          Status.DeliveryFailure
      value.contains("OUT_FOR_DELIVERY") -> Status.OutForDelivery
      value.contains("IN_TRANSIT") || value.contains("DISPATCHED") || value.contains("SHIPPED") ->
          Status.InTransit
      value.contains("ACCEPTED") || value.contains("AWAITING_DISPATCH") -> Status.Preadvice
      else -> Status.Unknown
    }
  }

  fun humanize(status: String): String =
      status
          .lowercase(Locale.ROOT)
          .split('_')
          .filter(String::isNotBlank)
          .joinToString(" ")
          .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}
