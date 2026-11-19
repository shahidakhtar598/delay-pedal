package com.naatdelay.dd7.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.naatdelay.dd7.data.local.dao.ExportDao
import com.naatdelay.dd7.data.local.dao.LicenseDao
import com.naatdelay.dd7.data.local.dao.PresetDao
import com.naatdelay.dd7.data.local.dao.ProjectDao
import com.naatdelay.dd7.data.local.entity.AudioProjectEntity
import com.naatdelay.dd7.data.local.entity.DeviceLicenseEntity
import com.naatdelay.dd7.data.local.entity.ExportHistoryEntity
import com.naatdelay.dd7.data.local.entity.PresetEntity

@Database(
    entities = [
        PresetEntity::class,
        AudioProjectEntity::class,
        ExportHistoryEntity::class,
        DeviceLicenseEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
    abstract fun projectDao(): ProjectDao
    abstract fun exportDao(): ExportDao
    abstract fun licenseDao(): LicenseDao

    companion object {
        val FACTORY_PRESETS: List<PresetEntity> = listOf(
            PresetEntity("preset-factory-01", "Classic Mehfil Hall", true, "ANALOG", 0.48f, 0.38f, 420f, false, 0.85f, true, 1L, 1L),
            PresetEntity("preset-factory-02", "Madina Ping-Pong", true, "MS_800", 0.52f, 0.42f, 480f, true, 0.90f, true, 2L, 2L),
            PresetEntity("preset-factory-03", "Studio Crisp Vocal", true, "MS_200", 0.36f, 0.25f, 185f, false, 0.80f, true, 3L, 3L),
            PresetEntity("preset-factory-04", "Slow Kalam Warm", true, "ANALOG", 0.56f, 0.48f, 620f, true, 0.85f, true, 4L, 4L),
            PresetEntity("preset-factory-05", "Chorus Shimmer Hamd", true, "MODULATE", 0.45f, 0.36f, 380f, true, 0.85f, true, 5L, 5L),
            PresetEntity("preset-factory-06", "Short Slapback Recite", true, "MS_50", 0.40f, 0.18f, 48f, false, 0.70f, true, 6L, 6L),
            PresetEntity("preset-factory-07", "Deep Jalsa Echo", true, "MS_3200", 0.50f, 0.46f, 950f, true, 0.92f, true, 7L, 7L),
            PresetEntity("preset-factory-08", "Clean Dry Monitor", true, "MS_200", 0.18f, 0.12f, 140f, false, 0.75f, true, 8L, 8L)
        )

        fun seedCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                FACTORY_PRESETS.forEach { p ->
                    db.execSQL(
                        """INSERT OR IGNORE INTO presets
                           (id, name, is_factory, delay_mode, effect_level, feedback, delay_time_ms,
                            is_ping_pong, ping_pong_width, anti_boom_cut, created_at, updated_at)
                           VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        arrayOf(
                            p.id, p.name, p.delayMode, p.effectLevel, p.feedback, p.delayTimeMs,
                            if (p.isPingPong) 1 else 0, p.pingPongWidth, if (p.antiBoomCut) 1 else 0,
                            p.createdAt, p.updatedAt
                        )
                    )
                }
            }
        }
    }
}
