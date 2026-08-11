// SPDX-License-Identifier: GPL-3.0-or-later
package dev.itsvic.parceltracker.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Parcel::class,
                parentColumns = ["id"],
                childColumns = ["parcelId"],
                onDelete = ForeignKey.CASCADE,
            )],
    indices =
        [
            Index(value = ["accountKey", "remotePackageId"], unique = true),
            Index(value = ["accountKey", "waybill"]),
        ],
)
data class AllegroPackageLink(
    @PrimaryKey val parcelId: Int,
    val accountKey: String,
    val remotePackageId: String,
    val carrierId: String,
    val carrierName: String,
    val waybill: String,
    val statusText: String,
    val readyForPickup: Boolean,
    val statusChangedAt: Instant,
    val lastSeenAt: Instant,
)

@Dao
interface AllegroPackageLinkDao {
  @Query("SELECT * FROM AllegroPackageLink WHERE parcelId = :parcelId")
  fun observeByParcelId(parcelId: Int): Flow<AllegroPackageLink?>

  @Query("SELECT * FROM AllegroPackageLink WHERE parcelId = :parcelId")
  suspend fun getByParcelId(parcelId: Int): AllegroPackageLink?

  @Query(
      """SELECT * FROM AllegroPackageLink
         WHERE accountKey = :accountKey
           AND (remotePackageId = :remotePackageId OR waybill = :waybill)
         LIMIT 1""")
  suspend fun findRemote(
      accountKey: String,
      remotePackageId: String,
      waybill: String,
  ): AllegroPackageLink?

  @Query(
      """SELECT * FROM AllegroPackageLink
         WHERE accountKey = :accountKey AND waybill = :waybill
         LIMIT 1""")
  suspend fun findByWaybill(accountKey: String, waybill: String): AllegroPackageLink?

  @Query(
      """UPDATE AllegroPackageLink SET readyForPickup = 0
         WHERE accountKey = :accountKey AND lastSeenAt < :syncStarted""")
  suspend fun revokeUnseenPickupCodes(accountKey: String, syncStarted: Instant)

  @Query("UPDATE AllegroPackageLink SET readyForPickup = 0 WHERE accountKey = :accountKey")
  suspend fun revokePickupCodes(accountKey: String)

  @Upsert suspend fun upsert(link: AllegroPackageLink)
}
