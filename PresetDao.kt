package com.naatdelay.dd7.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naatdelay.dd7.data.local.entity.PresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY is_factory DESC, updated_at DESC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): PresetEntity?

    @Query("SELECT COUNT(*) FROM presets WHERE is_factory = 0")
    suspend fun countCustomPresets(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: PresetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(presets: List<PresetEntity>)

    // Factory Preset Protection: only non-factory presets can ever be deleted
    @Query("DELETE FROM presets WHERE id = :id AND is_factory = 0")
    suspend fun deleteCustomById(id: String): Int
}
