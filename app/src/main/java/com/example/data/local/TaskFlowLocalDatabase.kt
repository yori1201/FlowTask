package com.example.data.local

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val status: String,
    val detail: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey val conflictId: String,
    val userId: String,
    val entityType: String,
    val entityId: String,
    val entityTitle: String,
    val localTitle: String,
    val localContent: String,
    val localRevision: Long,
    val cloudTitle: String,
    val cloudContent: String,
    val cloudRevision: Long,
    val detectedAtMillis: Long = System.currentTimeMillis(),
    val resolved: Boolean = false
)

@Entity(tableName = "note_drafts")
data class NoteDraftEntity(
    @PrimaryKey val draftKey: String,
    val userId: String,
    val noteId: String?,
    val title: String,
    val content: String,
    val category: String,
    val projectId: String?,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs WHERE userId = :userId ORDER BY createdAtMillis DESC LIMIT 60")
    fun observeLogs(userId: String): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs WHERE userId = :userId")
    suspend fun clearLogs(userId: String)
}

@Dao
interface SyncConflictDao {
    @Query("SELECT * FROM sync_conflicts WHERE userId = :userId AND resolved = 0 ORDER BY detectedAtMillis DESC")
    fun observeOpenConflicts(userId: String): Flow<List<SyncConflictEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflict(conflict: SyncConflictEntity)

    @Query("UPDATE sync_conflicts SET resolved = 1 WHERE conflictId = :conflictId")
    suspend fun markResolved(conflictId: String)
}

@Dao
interface NoteDraftDao {
    @Query("SELECT * FROM note_drafts WHERE draftKey = :draftKey LIMIT 1")
    suspend fun getDraft(draftKey: String): NoteDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: NoteDraftEntity)

    @Query("DELETE FROM note_drafts WHERE draftKey = :draftKey")
    suspend fun deleteDraft(draftKey: String)
}

@Database(
    entities = [SyncLogEntity::class, SyncConflictEntity::class, NoteDraftEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TaskFlowLocalDatabase : RoomDatabase() {
    abstract fun syncLogDao(): SyncLogDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun noteDraftDao(): NoteDraftDao

    companion object {
        @Volatile
        private var INSTANCE: TaskFlowLocalDatabase? = null

        fun getInstance(context: Context): TaskFlowLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskFlowLocalDatabase::class.java,
                    "taskflow_local.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
