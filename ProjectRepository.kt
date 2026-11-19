package com.naatdelay.dd7.data.repository

import com.naatdelay.dd7.data.local.dao.ExportDao
import com.naatdelay.dd7.data.local.dao.ProjectDao
import com.naatdelay.dd7.data.local.entity.AudioProjectEntity
import com.naatdelay.dd7.data.local.entity.ExportHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val exportDao: ExportDao
) {
    val recentProjects: Flow<List<AudioProjectEntity>> = projectDao.observeRecent()
    val exportHistory: Flow<List<ExportHistoryEntity>> = exportDao.observeHistory()

    suspend fun saveProject(project: AudioProjectEntity) = projectDao.upsert(project)
    suspend fun removeProject(id: String) = projectDao.deleteById(id)
    suspend fun recordExport(entry: ExportHistoryEntity) = exportDao.insert(entry)
}
