package com.naatdelay.dd7.data.repository

import com.naatdelay.dd7.data.local.dao.PresetDao
import com.naatdelay.dd7.data.local.entity.PresetEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SavePresetResult {
    data class Saved(val preset: PresetEntity) : SavePresetResult
    data object BlankNameError : SavePresetResult
    data object FactoryNameConflict : SavePresetResult
    data class DuplicateCustomExists(val existing: PresetEntity) : SavePresetResult
    data object TrialLimitReached : SavePresetResult
}

@Singleton
class PresetRepository @Inject constructor(
    private val presetDao: PresetDao
) {
    val presets: Flow<List<PresetEntity>> = presetDao.observeAll()

    suspend fun saveCustomPreset(
        rawName: String,
        delayMode: String,
        effectLevel: Float,
        feedback: Float,
        delayTimeMs: Float,
        isPingPong: Boolean,
        pingPongWidth: Float,
        antiBoomCut: Boolean,
        isProActivated: Boolean,
        allowOverwrite: Boolean = false
    ): SavePresetResult {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return SavePresetResult.BlankNameError

        val existing = presetDao.findByName(trimmed)
        if (existing != null) {
            if (existing.isFactory) return SavePresetResult.FactoryNameConflict
            if (!allowOverwrite) return SavePresetResult.DuplicateCustomExists(existing)
        } else if (!isProActivated && presetDao.countCustomPresets() >= 1) {
            return SavePresetResult.TrialLimitReached
        }

        val now = System.currentTimeMillis()
        val entity = PresetEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = trimmed,
            isFactory = false,
            delayMode = delayMode,
            effectLevel = effectLevel,
            feedback = feedback.coerceIn(0f, 0.95f),
            delayTimeMs = delayTimeMs.coerceIn(1f, 3200f),
            isPingPong = isPingPong,
            pingPongWidth = pingPongWidth,
            antiBoomCut = antiBoomCut,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        presetDao.upsert(entity)
        return SavePresetResult.Saved(entity)
    }

    suspend fun deleteCustomPreset(id: String): Boolean = presetDao.deleteCustomById(id) > 0
}
