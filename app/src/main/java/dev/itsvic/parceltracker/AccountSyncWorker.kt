// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.itsvic.parceltracker.allegro.AllegroRepository
import dev.itsvic.parceltracker.olx.OlxRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex

class AccountSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    applicationContext.syncAccountParcels(notifyChanges = true)
    return Result.success()
  }
}

suspend fun Context.syncAccountParcels(notifyChanges: Boolean = false) = supervisorScope {
  if (!accountSyncMutex.tryLock()) return@supervisorScope
  val context = applicationContext
  try {
    val allegro =
        async(Dispatchers.IO) {
          runAccountSync("Allegro") { AllegroRepository(context).sync(notifyChanges) }
        }
    val olx =
        async(Dispatchers.IO) {
          runAccountSync("OLX") { OlxRepository(context).sync(notifyChanges) }
        }
    allegro.await()
    olx.await()
  } finally {
    accountSyncMutex.unlock()
  }
}

private suspend fun runAccountSync(service: String, sync: suspend () -> Any) {
  try {
    sync()
  } catch (error: CancellationException) {
    throw error
  } catch (error: Exception) {
    Log.w("AccountSyncWorker", "Failed to sync $service packages", error)
  }
}

suspend fun Context.enqueueAccountSyncWorker() {
  val unmeteredOnly = dataStore.data.map { it[UNMETERED_ONLY] ?: false }.first()
  val constraints =
      Constraints.Builder()
          .setRequiredNetworkType(
              if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
          .build()
  val request =
      PeriodicWorkRequestBuilder<AccountSyncWorker>(1, TimeUnit.HOURS)
          .setInitialDelay(1, TimeUnit.HOURS)
          .setConstraints(constraints)
          .build()

  WorkManager.getInstance(this)
      .enqueueUniquePeriodicWork(
          ACCOUNT_SYNC_WORK_NAME,
          ExistingPeriodicWorkPolicy.UPDATE,
          request,
      )
}

private const val ACCOUNT_SYNC_WORK_NAME = "ParcelTrackerAccountSyncWorker"
private val accountSyncMutex = Mutex()
