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
            Index(value = ["accountKey", "orderId"], unique = true),
            Index(value = ["accountKey", "waybill"]),
        ],
)
data class OlxPackageLink(
    @PrimaryKey val parcelId: Int,
    val accountKey: String,
    val orderId: String,
    val shortOrderId: String,
    val waybill: String,
    val carrierId: String,
    val carrierName: String,
    val trackingUrl: String,
    val statusText: String,
    val statusChangedAt: Instant,
    val lastSeenAt: Instant,
)

@Dao
interface OlxPackageLinkDao {
  @Query("SELECT * FROM OlxPackageLink WHERE parcelId = :parcelId")
  fun observeByParcelId(parcelId: Int): Flow<OlxPackageLink?>

  @Query("SELECT * FROM OlxPackageLink WHERE parcelId = :parcelId LIMIT 1")
  suspend fun findByParcelId(parcelId: Int): OlxPackageLink?

  @Query(
      """SELECT * FROM OlxPackageLink
         WHERE accountKey = :accountKey AND orderId = :orderId
         LIMIT 1""")
  suspend fun findByOrder(accountKey: String, orderId: String): OlxPackageLink?

  @Query(
      """SELECT * FROM OlxPackageLink
         WHERE accountKey = :accountKey AND waybill = :waybill
         LIMIT 1""")
  suspend fun findByWaybill(accountKey: String, waybill: String): OlxPackageLink?

  @Query("SELECT * FROM OlxPackageLink WHERE waybill = :waybill ORDER BY lastSeenAt DESC LIMIT 1")
  suspend fun findLatestByWaybill(waybill: String): OlxPackageLink?

  @Upsert suspend fun upsert(link: OlxPackageLink)
}
