package com.cayxu.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cài đặt THẬT của [CoinLedger] bằng Room: sổ xu lưu ngay trên máy, mỗi nhiệm vụ xong ghi một dòng.
 * Cần thêm Room (xem README): room-runtime, room-ktx, và ksp/kapt cho room-compiler.
 * Bản này lưu cục bộ; muốn đồng bộ với server của bạn thì ghi thêm lên server trong [record] hoặc kéo về rồi gọi [record].
 */
@Entity(
    tableName = "coin_entries",
    indices = [Index("atMillis"), Index(value = ["source", "externalId"], unique = true)],
)
data class CoinEntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val coins: Long,
    val atMillis: Long,
    val source: String,
    val externalId: String?,
)

@Dao
interface CoinEntryDao {
    // IGNORE + chỉ mục duy nhất (source, externalId): ghi lại cùng nhiệm vụ không tạo dòng thứ hai.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: CoinEntryRow)

    @Query("SELECT * FROM coin_entries WHERE atMillis >= :from AND atMillis < :to ORDER BY atMillis DESC")
    fun between(from: Long, to: Long): Flow<List<CoinEntryRow>>

    @Query("SELECT * FROM coin_entries ORDER BY atMillis DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<CoinEntryRow>>
}

@Database(entities = [CoinEntryRow::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun coinEntries(): CoinEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cayxu_coins.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

class RoomCoinLedger(private val dao: CoinEntryDao) : CoinLedger {

    override suspend fun record(entry: CoinEntry) = dao.insert(entry.toRow())

    override fun observeDay(day: LocalDate, zone: ZoneId): Flow<List<CoinEntry>> {
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()   // tính theo ngày lịch nên đúng cả ngày đổi giờ
        return dao.between(from, to).map { rows -> rows.map { it.toEntry() } }
    }

    override fun observeRecent(limit: Int): Flow<List<CoinEntry>> =
        dao.recent(limit).map { rows -> rows.map { it.toEntry() } }
}

object CoinLedgerProvider {
    @Volatile
    private var ledger: CoinLedger? = null

    fun get(context: Context): CoinLedger {
        return ledger ?: synchronized(this) {
            ledger ?: RoomCoinLedger(AppDatabase.getInstance(context).coinEntries()).also { ledger = it }
        }
    }
}

private fun CoinEntry.toRow() = CoinEntryRow(0, kind.name, title, coins, atMillis, source, externalId)

private fun CoinEntryRow.toEntry() = CoinEntry(
    id = id,
    kind = runCatching { CoinEntryKind.valueOf(kind) }.getOrDefault(CoinEntryKind.Other),
    title = title, coins = coins, atMillis = atMillis, source = source, externalId = externalId,
)
