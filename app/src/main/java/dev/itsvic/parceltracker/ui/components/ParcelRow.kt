// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.Status
import dev.itsvic.parceltracker.api.getDeliveryServiceName
import dev.itsvic.parceltracker.db.Parcel
import dev.itsvic.parceltracker.ui.redactedParcelName
import dev.itsvic.parceltracker.ui.redactedText
import dev.itsvic.parceltracker.ui.theme.ParcelTrackerTheme

@Composable
fun ParcelRow(parcel: Parcel, status: Status?, onClick: () -> Unit) {
  Row(
      modifier = Modifier.clickable(onClick = onClick).fillMaxWidth().padding(16.dp, 12.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically) {
        if (status != null)
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) {
                  Icon(
                      painterResource(
                          when (status) {
                            Status.Preadvice -> R.drawable.outline_other_admission_24
                            Status.LockerboxAcceptedParcel ->
                                R.drawable.outline_deployed_code_update_24
                            Status.PickedUpByCourier -> R.drawable.outline_deployed_code_account_24
                            Status.InTransit -> R.drawable.outline_local_shipping_24
                            Status.InWarehouse -> R.drawable.outline_warehouse_24
                            Status.Customs -> R.drawable.outline_search_24
                            Status.OutForDelivery -> R.drawable.outline_delivery_truck_speed_24
                            Status.DeliveryFailure -> R.drawable.outline_error_24
                            Status.PickupTimeEndingSoon ->
                                R.drawable.outline_notifications_active_24
                            Status.AwaitingPickup -> R.drawable.outline_pin_drop_24
                            Status.Delivered,
                            Status.PickedUp -> R.drawable.outline_check_24
                            Status.DeliveredToNeighbor -> R.drawable.outline_holiday_village_24
                            Status.DeliveredToASafePlace -> R.drawable.outline_roofing_24
                            Status.DroppedAtCustomerService -> R.drawable.outline_support_agent_24
                            Status.ReturningToSender -> R.drawable.outline_arrow_top_left_24
                            Status.ReturnedToSender -> R.drawable.outline_arrow_top_left_24
                            Status.Delayed -> R.drawable.outline_deployed_code_history_24
                            Status.Damaged -> R.drawable.outline_deployed_code_alert_24
                            Status.Destroyed -> R.drawable.outline_destruction_24
                            else -> R.drawable.outline_question_mark_24
                          }),
                      stringResource(status.nameResource),
                      tint = MaterialTheme.colorScheme.primary)
                }

        Column {
          Text(redactedParcelName(parcel.humanName), color = MaterialTheme.colorScheme.onBackground)

          Text(
              "${stringResource(getDeliveryServiceName(parcel.service)!!)}: ${redactedText(parcel.parcelId)}",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
}

@Composable
@PreviewLightDark
fun ParcelRowPreview() {
  ParcelTrackerTheme {
    Box(modifier = Modifier.background(color = MaterialTheme.colorScheme.background)) {
      ParcelRow(
          Parcel(0, "My precious package", "EXMPL0001", null, Service.EXAMPLE),
          status = Status.InTransit,
          onClick = {},
      )
    }
  }
}
