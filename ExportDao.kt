package com.naatdelay.dd7.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naatdelay.dd7.data.local.entity.ExportHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportDao {
    @Query("SELECT * FROM export_history ORDER BY exported_at DESC LIMIT 30")
    fun observeHistory(): Flow<List<ExportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ExportHistoryEntity)
}
