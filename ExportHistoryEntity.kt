package com.naatdelay.dd7.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "export_history",
    foreignKeys = [
        ForeignKey(
            entity = AudioProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("project_id")]
)
data class ExportHistoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String?,
    @ColumnInfo(name = "output_file_name") val outputFileName: String,
    @ColumnInfo(name = "output_uri") val outputUri: String,
    val format: String, // WAV_16BIT, MP3_192K, MP3_320K
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "tail_padding_ms") val tailPaddingMs: Int,
    @ColumnInfo(name = "knob_snapshot_json") val knobSnapshotJson: String,
    @ColumnInfo(name = "exported_at") val exportedAt: Long
)
