package com.naatdelay.dd7.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_projects",
    foreignKeys = [
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["last_preset_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("last_preset_id")]
)
data class AudioProjectEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "source_uri") val sourceUri: String,
    @ColumnInfo(name = "cached_pcm_path") val cachedPcmPath: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int,
    @ColumnInfo(name = "waveform_peaks_json") val waveformPeaksJson: String?,
    @ColumnInfo(name = "last_preset_id") val lastPresetId: String?,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long
)
