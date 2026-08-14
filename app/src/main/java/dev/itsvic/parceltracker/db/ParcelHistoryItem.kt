package dev.itsvic.parceltracker.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

@Entity
data class ParcelHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parcelId: Int,
    val description: String,
    val time: LocalDateTime,
    val location: String,
)

data class ParcelId(
    val parcelId: Int,
)

@Dao
interface ParcelHistoryDao {
  @Query("SELECT * FROM parcelhistoryitem WHERE parcelId=:id")
  fun getAllById(id: Int): Flow<List<ParcelHistoryItem>>

  @Query("SELECT * FROM parcelhistoryitem WHERE parcelId=:id ORDER BY time DESC")
  suspend fun getById(id: Int): List<ParcelHistoryItem>

  @Insert suspend fun insert(items: List<ParcelHistoryItem>)

  @Query("DELETE FROM parcelhistoryitem WHERE parcelId=:id") suspend fun deleteByParcelId(id: Int)

  @Delete(entity = ParcelHistoryItem::class) suspend fun deleteByParcelId(parcelId: ParcelId)
}
