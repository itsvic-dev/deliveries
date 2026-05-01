// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerTargetProvider
import dev.itsvic.parceltracker.api.getParcel
import dev.itsvic.parceltracker.db.ParcelStatus
import dev.itsvic.parceltracker.smartspacer.targets.ParcelStatusTarget
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class NotificationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val db = ParcelApplication.db
    val parcelDao = db.parcelDao()
    val statusDao = db.parcelStatusDao()
    val zone = ZoneId.systemDefault()
    Log.d("NotificationWorker", "I ran!")

    withContext(Dispatchers.IO) {
      val parcels = parcelDao.getAllNonArchivedWithStatusAsync()
      Log.d("NotificationWorker", "Got parcels: $parcels")

      for (parcelWithStatus in parcels) {
        val parcel = parcelWithStatus.parcel
        val oldStatus = parcelWithStatus.status

        Log.d("NotificationWorker", "Fetching parcel status for $parcel")
        val apiParcel =
            try {
              applicationContext.getParcel(parcel.parcelId, parcel.postalCode, parcel.service)
            } catch (e: Exception) {
              Log.d("NotificationWorker", "Failed to fetch, skipping", e)
              continue
            }

        val lastChange = apiParcel.history.first().time.atZone(zone).toInstant()

        when {
          oldStatus == null -> {
            Log.d("NotificationWorker", "Parcel did not have a status before, will only add one.")
            statusDao.insert(ParcelStatus(parcel.id, apiParcel.currentStatus, lastChange))
          }

          oldStatus.lastChange != lastChange -> {
            Log.d("NotificationWorker", "Parcel has had updates since then, push a notification!")
            applicationContext.sendNotification(
                parcel, apiParcel.currentStatus, apiParcel.history.first())
            statusDao.update(ParcelStatus(parcel.id, apiParcel.currentStatus, lastChange))
          }

          else -> Log.d("NotificationWorker", "Parcel has not had any updates yet.")
        }
      }
    }

    // TODO: this most likely has to be moved
    SmartspacerTargetProvider.notifyChange(applicationContext, ParcelStatusTarget::class.java)

    return Result.success()
  }
}

private const val WORK_NAME = "ParcelTrackerNotificationWorker"

suspend fun Context.enqueueNotificationWorker() {
  val unmeteredOnly = this.dataStore.data.map { it[UNMETERED_ONLY] ?: false }.first()

  val constraints =
      Constraints.Builder()
          .setRequiredNetworkType(
              if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
          .build()

  val request =
      PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
          .setConstraints(constraints)
          .build()

  WorkManager.getInstance(this)
      .enqueueUniquePeriodicWork(
          WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
}

suspend fun Context.enqueueWorkerIfNotQueued() {
  val wm = WorkManager.getInstance(this)
  val infos = wm.getWorkInfosForUniqueWork(WORK_NAME).await()
  if (infos.isEmpty() ||
      (infos.first().state != WorkInfo.State.ENQUEUED &&
          infos.first().state != WorkInfo.State.RUNNING)) {
    this.enqueueNotificationWorker()
  } else {
    Log.d("NotificationWorker", "Already enqueued/running, not doing it again")
  }
}
