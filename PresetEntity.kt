package com.naatdelay.dd7.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "presets",
    indices = [Index(value = ["name"], unique = true)]
)
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "is_factory") val isFactory: Boolean = false,
    @ColumnInfo(name = "delay_mode") val delayMode: String, // MS_50, MS_200, MS_800, MS_3200, ANALOG, MODULATE
    @ColumnInfo(name = "effect_level") val effectLevel: Float,
    val feedback: Float,
    @ColumnInfo(name = "delay_time_ms") val delayTimeMs: Float,
    @ColumnInfo(name = "is_ping_pong") val isPingPong: Boolean = false,
    @ColumnInfo(name = "ping_pong_width") val pingPongWidth: Float = 0.85f,
    @ColumnInfo(name = "anti_boom_cut") val antiBoomCut: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
