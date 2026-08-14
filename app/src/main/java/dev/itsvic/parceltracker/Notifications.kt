// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.itsvic.parceltracker.api.ParcelHistoryItem
import dev.itsvic.parceltracker.api.Status
import dev.itsvic.parceltracker.db.Parcel

const val CHANNEL_ID = "ParcelTrackerEvents"

suspend fun Context.sendNotification(parcel: Parcel, status: Status, event: ParcelHistoryItem) {
  val context = this
  val statusString = getString(status.nameResource)
  val redact = redactIdentifiableDetailsEnabled()
  val title =
      if (redact) {
        "${getString(R.string.redacted_parcel_name)}: $statusString"
      } else {
        "${parcel.humanName}: $statusString"
      }
  val body = if (redact) getString(R.string.redacted) else event.description

  val intent =
      Intent(this, MainActivity::class.java).apply {
        putExtra("openParcel", parcel.id)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
  val pendingIntent =
      PendingIntent.getActivity(
          this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

  val builder =
      NotificationCompat.Builder(this, CHANNEL_ID)
          .setSmallIcon(R.drawable.package_2)
          .setContentTitle(title)
          .setContentText(body)
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setContentIntent(pendingIntent)
          .setAutoCancel(true)

  with(NotificationManagerCompat.from(this)) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED) {
      return
    }
    notify(parcel.id, builder.build())
  }
}

fun Context.createNotificationChannel() {
  val name = getString(R.string.channel_name)
  val description = getString(R.string.channel_description)
  val importance = NotificationManager.IMPORTANCE_HIGH
  val channel = NotificationChannel(CHANNEL_ID, name, importance)
  channel.description = description
  val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  notificationManager.createNotificationChannel(channel)
}
