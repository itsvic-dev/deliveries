// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import dev.itsvic.parceltracker.R
import java.time.LocalDateTime
import kotlin.random.Random

// "Example Post" backend.
// This is used only by Demo Mode. This is not a real API backend.

object ExampleDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_example
  override val acceptsPostCode: Boolean = true
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(trackingId: String, postCode: String?): Parcel {
    if (!exampleParcels.containsKey(trackingId)) throw ParcelNonExistentException()
    return exampleParcels[trackingId]!!
  }

  /** Defines a parcel history item $hoursBack hours +/- 30 minutes behind the current time. */
  private fun historyItem(
      status: String,
      hoursBack: Number,
  ): ParcelHistoryItem {
    val baseDate = LocalDateTime.now().minusHours(hoursBack.toLong())
    val randomizedDate = baseDate.plusSeconds(Random.nextLong(-1800, 1800))
    return ParcelHistoryItem(status, randomizedDate, "Mars")
  }

  private val exampleParcels =
      mapOf(
          "2503894188" to
              Parcel(
                  "2503894188",
                  listOf(
                      historyItem("Delivered", 1),
                      historyItem("Out for delivery", 5),
                      historyItem("Arrived at local warehouse", 24),
                      historyItem("In transit", 48),
                      historyItem("Label created", 96),
                  ),
                  Status.Delivered),
          "7301626157" to
              Parcel(
                  "7301626157",
                  listOf(
                      historyItem("Awaiting pickup at parcel locker", 2),
                      historyItem("Out for delivery", 5),
                      historyItem("Arrived at local warehouse", 24),
                      historyItem("In transit", 48),
                      historyItem("Label created", 96),
                  ),
                  Status.AwaitingPickup),
          "6171197286" to
              Parcel("6171197286", listOf(historyItem("Label created", 2)), Status.Preadvice),
      )
}
