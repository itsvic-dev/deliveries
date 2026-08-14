// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.olx

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import dev.itsvic.parceltracker.ParcelApplication
import dev.itsvic.parceltracker.api.Parcel as ApiParcel
import dev.itsvic.parceltracker.api.ParcelHistoryItem as ApiHistoryItem
import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.Status
import dev.itsvic.parceltracker.api.getDeliveryService
import dev.itsvic.parceltracker.db.OlxPackageLink
import dev.itsvic.parceltracker.db.Parcel
import dev.itsvic.parceltracker.db.ParcelHistoryItem
import dev.itsvic.parceltracker.db.ParcelStatus
import dev.itsvic.parceltracker.sendNotification
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OlxRepository(context: Context) {
  private val appContext = context.applicationContext
  private val store = OlxSessionStore(appContext)
  private val db = ParcelApplication.db

  fun session(): OlxSession? = store.load()

  fun beginLogin(): OlxAuthorizationRequest =
      OlxAuthProtocol.createAuthorizationRequest().also(store::savePendingAuthorization)

  suspend fun completeLogin(callbackUrl: String): OlxSession =
      accountMutex.withLock {
        val code =
            OlxAuthProtocol.authorizationCodeFromCallback(callbackUrl)
                ?: throw OlxException("OLX returned an invalid sign-in callback.")
        val request =
            store.loadPendingAuthorization()
                ?: throw OlxException("The pending OLX sign-in request was lost. Try again.")
        try {
          OlxClient(store, debugInterceptor = createOlxDebugInterceptor(appContext))
              .exchangeCode(request, code)
              .also(store::save)
        } finally {
          store.clearPendingAuthorization()
        }
      }

  fun cancelLogin() = store.clearPendingAuthorization()

  suspend fun logout() =
      accountMutex.withLock {
        store.clearPendingAuthorization()
        store.clearSession()
      }

  suspend fun sync(notifyChanges: Boolean = false): Int =
      accountMutex.withLock {
        val saved = store.load() ?: return 0
        val accountKey = olxAccountKey(saved.accountId)
        val orders =
            OlxClient(store, debugInterceptor = createOlxDebugInterceptor(appContext)).purchases()
        val now = Instant.now()
        val notifications = mutableListOf<StatusChange>()
        var syncedCount = 0

        val candidates =
            orders.mapNotNull { order ->
              val oldLink = db.olxPackageLinkDao().findByOrder(accountKey, order.id)
              val waybill = OlxParser.trackingNumber(order) ?: oldLink?.waybill
              if (waybill.isNullOrBlank()) return@mapNotNull null

              val delivery = order.fulfillment?.product
              val carrierId =
                  delivery?.option?.name?.takeIf(String::isNotBlank) ?: oldLink?.carrierId
              val carrierName =
                  delivery?.shortName?.takeIf(String::isNotBlank)
                      ?: delivery?.option?.fallback?.takeIf(String::isNotBlank)
                      ?: oldLink?.carrierName?.takeIf(String::isNotBlank)
                      ?: carrierId
              val providerService = OlxParser.deliveryService(carrierId, carrierName)
              val statusText =
                  order.status?.name?.takeIf(String::isNotBlank) ?: oldLink?.statusText.orEmpty()
              val shouldRefreshProvider =
                  OlxParser.trackingNumber(order) != null || oldLink?.statusText != statusText
              SyncCandidate(
                  order = order,
                  oldLink = oldLink,
                  waybill = waybill,
                  carrierId = carrierId.orEmpty(),
                  carrierName = carrierName.orEmpty(),
                  trackingUrl = OlxParser.trackingUrl(order) ?: oldLink?.trackingUrl.orEmpty(),
                  statusText = statusText,
                  providerService = providerService,
                  providerParcel =
                      providerService
                          ?.takeIf { shouldRefreshProvider }
                          ?.let { service -> fetchProviderParcel(service, waybill) },
              )
            }

        db.withTransaction {
          candidates.forEach { candidate ->
            val order = candidate.order
            val oldLink = candidate.oldLink
            val waybill = candidate.waybill
            val existingParcel = oldLink?.let { db.parcelDao().getByIdAsync(it.parcelId) }
            val parcelService =
                candidate.providerService
                    ?: existingParcel?.service?.takeUnless { it == Service.OLX_ACCOUNT }
                    ?: Service.OLX_ACCOUNT

            val title = OlxParser.title(order, waybill)
            val localParcel =
                if (oldLink == null) {
                  val id =
                      db.parcelDao()
                          .insert(
                              Parcel(
                                  humanName = title,
                                  parcelId = waybill,
                                  postalCode = null,
                                  service = parcelService,
                              ))
                          .toInt()
                  db.parcelDao().getByIdAsync(id)!!
                } else {
                  val existing = existingParcel ?: return@forEach
                  val updated =
                      existing.copy(
                          humanName =
                              if (existing.humanName.isBlank()) title else existing.humanName,
                          parcelId = waybill,
                          postalCode = null,
                          service = parcelService,
                      )
                  if (updated != existing) db.parcelDao().update(updated)
                  updated
                }

            val statusText = candidate.statusText
            val oldStatus = db.parcelStatusDao().getOrNull(localParcel.id)
            val fallbackStatus = OlxParser.statusToAppStatus(statusText)
            val appStatus =
                candidate.providerParcel?.currentStatus?.takeUnless { it == Status.Unknown }
                    ?: fallbackStatus.takeUnless { it == Status.Unknown }
                    ?: oldStatus?.status
                    ?: Status.Unknown
            val latestProviderEvent = candidate.providerParcel?.history?.maxByOrNull { it.time }
            val providerChangedAt = latestProviderEvent?.time?.atInstant()
            val statusChanged = oldStatus != null && oldStatus.status != appStatus
            val orderStatusChanged = oldLink != null && oldLink.statusText != statusText
            val providerHistoryChanged =
                oldStatus != null &&
                    providerChangedAt != null &&
                    oldStatus.lastChange != providerChangedAt
            val trackingChanged = statusChanged || orderStatusChanged || providerHistoryChanged
            val changedAt =
                providerChangedAt
                    ?: if (oldStatus == null || trackingChanged) {
                      now
                    } else {
                      oldStatus.lastChange
                    }
            val link =
                OlxPackageLink(
                    parcelId = localParcel.id,
                    accountKey = accountKey,
                    orderId = order.id,
                    shortOrderId = order.shortId.orEmpty(),
                    waybill = waybill,
                    carrierId = candidate.carrierId,
                    carrierName = candidate.carrierName,
                    trackingUrl = candidate.trackingUrl,
                    statusText = statusText,
                    statusChangedAt = changedAt,
                    lastSeenAt = now,
                )
            db.olxPackageLinkDao().upsert(link)
            db.parcelStatusDao().upsert(ParcelStatus(localParcel.id, appStatus, changedAt))

            val providerHistory = candidate.providerParcel?.history.orEmpty()
            val storedHistory = db.parcelHistoryDao().getById(localParcel.id)
            when {
              providerHistory.isNotEmpty() -> {
                db.parcelHistoryDao().deleteByParcelId(localParcel.id)
                db.parcelHistoryDao()
                    .insert(
                        providerHistory.map {
                          ParcelHistoryItem(
                              parcelId = localParcel.id,
                              description = it.description,
                              time = it.time,
                              location = it.location,
                          )
                        })
              }
              storedHistory.isEmpty() || orderStatusChanged -> {
                val description = OlxParser.humanize(statusText).ifBlank { "OLX order updated" }
                val fallbackHistory =
                    ParcelHistoryItem(
                        parcelId = localParcel.id,
                        description = description,
                        time = changedAt.atLocalTime(),
                        location = "",
                    )
                db.parcelHistoryDao().deleteByParcelId(localParcel.id)
                db.parcelHistoryDao().insert(listOf(fallbackHistory) + storedHistory)
              }
            }

            if (notifyChanges && trackingChanged && !localParcel.isArchived) {
              notifications +=
                  StatusChange(
                      localParcel,
                      appStatus,
                      latestProviderEvent
                          ?: ApiHistoryItem(
                              OlxParser.humanize(statusText).ifBlank { "OLX order updated" },
                              changedAt.atLocalTime(),
                              "",
                          ),
                  )
            }
            syncedCount++
          }
        }

        notifications.forEach { change ->
          appContext.sendNotification(
              change.parcel,
              change.status,
              change.history,
          )
        }
        syncedCount
      }

  suspend fun fetchParcel(trackingNumber: String): ApiParcel {
    val saved = store.load()
    val link =
        saved?.let {
          db.olxPackageLinkDao().findByWaybill(olxAccountKey(it.accountId), trackingNumber)
        } ?: db.olxPackageLinkDao().findLatestByWaybill(trackingNumber)
    val parcel = link?.let { db.parcelDao().getByIdAsync(it.parcelId) }
    val status = link?.let { db.parcelStatusDao().getOrNull(it.parcelId) }
    val history = link?.let { db.parcelHistoryDao().getById(it.parcelId) }.orEmpty()
    val changedAt = link?.statusChangedAt ?: status?.lastChange ?: Instant.now()
    val fallbackDescription = link?.statusText?.let(OlxParser::humanize) ?: "Saved OLX status"
    return ApiParcel(
        id = parcel?.parcelId ?: trackingNumber,
        history =
            history
                .map { ApiHistoryItem(it.description, it.time, it.location) }
                .ifEmpty {
                  listOf(ApiHistoryItem(fallbackDescription, changedAt.atLocalTime(), ""))
                },
        currentStatus = status?.status ?: Status.Unknown,
    )
  }

  private suspend fun fetchProviderParcel(service: Service, waybill: String): ApiParcel? {
    val provider = getDeliveryService(service) ?: return null
    if (provider.requiresPostCode) return null
    return try {
      provider.getParcel(appContext, waybill, null)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      Log.w(TAG, "Could not refresh OLX parcel $waybill through $service", exception)
      null
    }
  }

  private fun Instant.atLocalTime(): LocalDateTime =
      atZone(ZoneId.systemDefault()).toLocalDateTime()

  private fun LocalDateTime.atInstant(): Instant = atZone(ZoneId.systemDefault()).toInstant()

  private data class SyncCandidate(
      val order: OlxOrderDto,
      val oldLink: OlxPackageLink?,
      val waybill: String,
      val carrierId: String,
      val carrierName: String,
      val trackingUrl: String,
      val statusText: String,
      val providerService: Service?,
      val providerParcel: ApiParcel?,
  )

  private data class StatusChange(
      val parcel: Parcel,
      val status: Status,
      val history: ApiHistoryItem,
  )

  private companion object {
    const val TAG = "OlxRepository"
    val accountMutex = Mutex()
  }
}

internal fun olxAccountKey(accountId: String): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
  return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
