package com.naatdelay.dd7.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naatdelay.dd7.data.local.entity.DeviceLicenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LicenseDao {
    @Query("SELECT * FROM device_license WHERE singleton_id = 1 LIMIT 1")
    fun observeLicense(): Flow<DeviceLicenseEntity?>

    @Query("SELECT * FROM device_license WHERE singleton_id = 1 LIMIT 1")
    suspend fun getLicense(): DeviceLicenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceLicenseEntity)
}
