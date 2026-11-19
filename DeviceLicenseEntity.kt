package com.naatdelay.dd7.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_license")
data class DeviceLicenseEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singletonId: Int = 1,
    @ColumnInfo(name = "device_fingerprint") val deviceFingerprint: String,
    @ColumnInfo(name = "license_key") val licenseKey: String? = null,
    @ColumnInfo(name = "signed_token") val signedToken: String? = null,
    val status: String = "TRIAL", // TRIAL or ACTIVATED
    @ColumnInfo(name = "activated_at") val activatedAt: Long? = null
)
