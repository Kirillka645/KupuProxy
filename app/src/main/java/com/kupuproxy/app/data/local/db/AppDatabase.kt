package com.kupuproxy.app.data.local.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey val dedupeKey: String,
    val url: String,
    val host: String,
    val port: Int,
    val secret: String,
    val reliabilityScore: Int,
    val lastOkAt: Long?,
    val failStreak: Int = 0
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val kind: String,
    val enabled: Boolean,
    val isUser: Boolean
)

@Entity(tableName = "check_history")
data class CheckHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val ok: Boolean,
    val rttMs: Int,
    val checkedAt: Long
)

@Entity(tableName = "blacklist")
data class BlacklistEntity(
    @PrimaryKey val dedupeKey: String,
    val untilEpochMs: Long,
    val reason: String
)

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies ORDER BY reliabilityScore DESC")
    suspend fun all(): List<ProxyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProxyEntity>)

    @Query("DELETE FROM proxies")
    suspend fun clear()
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources")
    suspend fun all(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BlacklistDao {
    @Query("SELECT * FROM blacklist WHERE untilEpochMs > :now")
    suspend fun active(now: Long): List<BlacklistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BlacklistEntity)

    @Query("DELETE FROM blacklist WHERE untilEpochMs <= :now")
    suspend fun purgeExpired(now: Long)
}

@Dao
interface CheckHistoryDao {
    @Insert
    suspend fun insert(item: CheckHistoryEntity)

    @Query("SELECT * FROM check_history WHERE dedupeKey = :key ORDER BY checkedAt DESC LIMIT :limit")
    suspend fun recent(key: String, limit: Int): List<CheckHistoryEntity>
}

@Database(
    entities = [
        ProxyEntity::class,
        SourceEntity::class,
        CheckHistoryEntity::class,
        BlacklistEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun proxyDao(): ProxyDao
    abstract fun sourceDao(): SourceDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun checkHistoryDao(): CheckHistoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kupuproxy.db"
                ).build().also { instance = it }
            }
        }
    }
}
