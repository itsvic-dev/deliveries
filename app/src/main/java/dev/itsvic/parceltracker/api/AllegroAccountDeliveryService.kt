// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.api

import android.content.Context
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.allegro.AllegroRepository

object AllegroAccountDeliveryService : DeliveryService {
  override val nameResource: Int = R.string.service_allegro_account
  override val acceptsPostCode: Boolean = false
  override val requiresPostCode: Boolean = false

  override suspend fun getParcel(
      context: Context,
      trackingId: String,
      postalCode: String?,
  ): Parcel = AllegroRepository(context).fetchParcel(trackingId)
}
