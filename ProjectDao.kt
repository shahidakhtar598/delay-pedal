package com.naatdelay.dd7.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naatdelay.dd7.data.local.entity.AudioProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM audio_projects ORDER BY last_opened_at DESC LIMIT 20")
    fun observeRecent(): Flow<List<AudioProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: AudioProjectEntity)

    @Query("DELETE FROM audio_projects WHERE id = :id")
    suspend fun deleteById(id: String)
}
