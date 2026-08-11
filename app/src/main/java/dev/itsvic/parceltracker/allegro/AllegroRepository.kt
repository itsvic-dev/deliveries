// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.allegro

import android.content.Context
import androidx.room.withTransaction
import dev.itsvic.parceltracker.ParcelApplication
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.api.Parcel as ApiParcel
import dev.itsvic.parceltracker.api.ParcelHistoryItem as ApiHistoryItem
import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.db.AllegroPackageLink
import dev.itsvic.parceltracker.db.Parcel
import dev.itsvic.parceltracker.db.ParcelStatus
import dev.itsvic.parceltracker.sendNotification
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AllegroRepository(context: Context) {
  private val appContext = context.applicationContext
  private val store = AllegroSessionStore(appContext)
  private val db = ParcelApplication.db

  fun session(): AllegroSession? = store.load()

  suspend fun login(username: String, password: String): AllegroSession =
      accountMutex.withLock {
        val previous = store.load()
        val session = AllegroClient().login(username.trim(), password)
        if (previous != null &&
            allegroAccountKey(previous.username) != allegroAccountKey(session.username)) {
          db.allegroPackageLinkDao().revokePickupCodes(allegroAccountKey(previous.username))
        }
        store.save(session)
        session
      }

  suspend fun logout() =
      accountMutex.withLock {
        val saved = store.load()
        try {
          saved?.let {
            db.allegroPackageLinkDao().revokePickupCodes(allegroAccountKey(it.username))
          }
        } finally {
          store.clear()
        }
      }

  suspend fun sync(notifyChanges: Boolean = false): Int =
      accountMutex.withLock {
        val saved = store.load() ?: return 0
        val accountKey = allegroAccountKey(saved.username)
        val client =
            AllegroClient(saved, debugInterceptor = createAllegroDebugInterceptor(appContext))
        val packages =
            try {
              client.fetchDashboard()
            } catch (error: AllegroSessionExpiredException) {
              clearSession(accountKey)
              throw error
            }
        client.session?.let(store::save)
        val now = Instant.now()
        val notifications = mutableListOf<StatusChange>()
        var syncedCount = 0

        db.withTransaction {
          packages.forEach { remote ->
            val waybill = remote.trackingNumber.ifBlank { remote.packageId }
            if (waybill.isBlank()) return@forEach
            val oldLink =
                db.allegroPackageLinkDao().findRemote(accountKey, remote.packageId, waybill)
            val localParcel =
                if (oldLink == null) {
                  val id =
                      db.parcelDao()
                          .insert(
                              Parcel(
                                  humanName = remote.title.ifBlank { waybill },
                                  parcelId = waybill,
                                  postalCode = null,
                                  service = Service.ALLEGRO_ACCOUNT,
                              ))
                          .toInt()
                  db.parcelDao().getByIdAsync(id)!!
                } else {
                  val existing = db.parcelDao().getByIdAsync(oldLink.parcelId) ?: return@forEach
                  if (existing.parcelId != waybill || existing.service != Service.ALLEGRO_ACCOUNT) {
                    existing
                        .copy(
                            parcelId = waybill,
                            postalCode = null,
                            service = Service.ALLEGRO_ACCOUNT,
                        )
                        .also { db.parcelDao().update(it) }
                  } else {
                    existing
                  }
                }
            val statusChanged =
                oldLink != null &&
                    (oldLink.statusText != remote.status ||
                        oldLink.readyForPickup != remote.readyForPickup)
            val changedAt = if (oldLink == null || statusChanged) now else oldLink.statusChangedAt
            val link =
                AllegroPackageLink(
                    parcelId = localParcel.id,
                    accountKey = accountKey,
                    remotePackageId = remote.packageId,
                    carrierId = remote.carrierId.ifBlank { carrierId(remote.carrier) },
                    carrierName = remote.carrier,
                    waybill = waybill,
                    statusText = remote.status,
                    readyForPickup = remote.readyForPickup,
                    statusChangedAt = changedAt,
                    lastSeenAt = now,
                )
            db.allegroPackageLinkDao().upsert(link)
            val appStatus = AllegroParser.statusToAppStatus(remote.status, remote.readyForPickup)
            db.parcelStatusDao().upsert(ParcelStatus(localParcel.id, appStatus, changedAt))
            if (notifyChanges && statusChanged && !localParcel.isArchived) {
              notifications += StatusChange(localParcel, appStatus, remote.status)
            }
            syncedCount++
          }
          db.allegroPackageLinkDao().revokeUnseenPickupCodes(accountKey, now)
        }

        notifications.forEach { change ->
          appContext.sendNotification(
              change.parcel,
              change.status,
              ApiHistoryItem(change.description, LocalDateTime.now(), ""),
          )
        }
        syncedCount
      }

  suspend fun fetchParcel(trackingNumber: String): ApiParcel =
      accountMutex.withLock {
        val saved = store.load()
        val link =
            saved?.let {
              db.allegroPackageLinkDao()
                  .findByWaybill(allegroAccountKey(it.username), trackingNumber)
            } ?: return cachedParcel(trackingNumber)
        val client =
            AllegroClient(saved, debugInterceptor = createAllegroDebugInterceptor(appContext))
        val original = link.toRemotePackage()
        val details =
            try {
              client.fetchPackageDetails(original)
            } catch (error: AllegroSessionExpiredException) {
              clearSession(allegroAccountKey(saved.username))
              return link.toApiParcel(original)
            }
        client.session?.let(store::save)
        return link.toApiParcel(details)
      }

  suspend fun fetchPickupDetails(link: AllegroPackageLink): AllegroPickupDetails =
      accountMutex.withLock {
        val saved =
            store.load()
                ?: throw AllegroSessionExpiredException("Log in to your Allegro account first.")
        if (allegroAccountKey(saved.username) != link.accountKey) {
          throw AllegroSessionExpiredException("Log in to the Allegro account for this package.")
        }
        val client =
            AllegroClient(saved, debugInterceptor = createAllegroDebugInterceptor(appContext))
        return try {
          client.fetchPickupDetails(link.carrierId, link.waybill).also {
            client.session?.let(store::save)
          }
        } catch (error: AllegroSessionExpiredException) {
          clearSession(link.accountKey)
          throw error
        }
      }

  private suspend fun clearSession(accountKey: String) {
    try {
      db.allegroPackageLinkDao().revokePickupCodes(accountKey)
    } finally {
      store.clear()
    }
  }

  private suspend fun cachedParcel(trackingNumber: String): ApiParcel {
    val parcel =
        db.parcelDao().getAllNonArchivedWithStatusAsync().firstOrNull {
          it.parcel.parcelId == trackingNumber && it.parcel.service == Service.ALLEGRO_ACCOUNT
        }
    val status = parcel?.status
    val time =
        status?.lastChange?.atZone(ZoneId.systemDefault())?.toLocalDateTime() ?: LocalDateTime.now()
    return ApiParcel(
        trackingNumber,
        listOf(ApiHistoryItem("Saved Allegro status", time, "")),
        status?.status ?: dev.itsvic.parceltracker.api.Status.Unknown,
    )
  }

  private fun AllegroPackageLink.toRemotePackage() =
      AllegroPackage(
          packageId = remotePackageId,
          title = waybill,
          status = statusText,
          carrier = carrierName,
          trackingNumber = waybill,
          readyForPickup = readyForPickup,
          carrierId = carrierId,
      )

  private fun AllegroPackageLink.toApiParcel(remote: AllegroPackage): ApiParcel {
    val history =
        remote.history
            .map {
              ApiHistoryItem(
                  description = it.status,
                  time = parseTimestamp(it.timestamp) ?: statusChangedAt.atLocalTime(),
                  location = "",
              )
            }
            .ifEmpty { listOf(ApiHistoryItem(remote.status, statusChangedAt.atLocalTime(), "")) }
    val properties = buildMap {
      if (remote.eta.isNotBlank()) put(R.string.property_eta, remote.eta)
      if (remote.pickupPoint.isNotBlank()) {
        put(R.string.property_pickup_point, remote.pickupPoint)
      }
    }
    return ApiParcel(
        id = waybill,
        history = history,
        currentStatus = AllegroParser.statusToAppStatus(remote.status, remote.readyForPickup),
        properties = properties,
    )
  }

  private fun parseTimestamp(value: String): LocalDateTime? {
    if (value.isBlank()) return null
    listOf(
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("pl-PL")),
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        )
        .forEach { formatter ->
          try {
            return LocalDateTime.parse(value, formatter)
          } catch (_: DateTimeParseException) {}
        }
    return null
  }

  private fun Instant.atLocalTime(): LocalDateTime =
      atZone(ZoneId.systemDefault()).toLocalDateTime()

  private fun carrierId(carrier: String): String =
      when (carrier.lowercase()) {
        "allegro",
        "allegro one",
        "allegro one box" -> "ALLEGRO"
        "inpost" -> "INPOST"
        else -> carrier.uppercase().replace(' ', '_')
      }

  private data class StatusChange(
      val parcel: Parcel,
      val status: dev.itsvic.parceltracker.api.Status,
      val description: String,
  )

  companion object {
    private val accountMutex = Mutex()
  }
}

internal fun allegroAccountKey(username: String): String {
  val digest =
      MessageDigest.getInstance("SHA-256")
          .digest(username.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
  val alphabet = "0123456789abcdef"
  return buildString(digest.size * 2) {
    digest.forEach { byte ->
      val value = byte.toInt() and 0xff
      append(alphabet[value ushr 4])
      append(alphabet[value and 0x0f])
    }
  }
}
