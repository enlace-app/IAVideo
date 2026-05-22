package com.example.data.repository

import com.example.data.database.VideoProjectDao
import com.example.data.model.VideoProjectEntity
import com.example.data.model.SceneEntity
import com.example.data.model.SubtitleSegmentEntity
import com.example.data.model.FullVideoProject
import kotlinx.coroutines.flow.Flow

class VideoRepository(private val dao: VideoProjectDao) {
    val allProjects: Flow<List<VideoProjectEntity>> = dao.getAllProjects()

    suspend fun getProjectById(id: Int): VideoProjectEntity? {
        return dao.getProjectById(id)
    }

    suspend fun getFullProject(id: Int): FullVideoProject? {
        val proj = dao.getProjectById(id) ?: return null
        val scenes = dao.getScenesByProjectIdList(id)
        val subtitles = dao.getSubtitlesByProjectIdList(id)
        return FullVideoProject(proj, scenes, subtitles)
    }

    suspend fun createProject(
        project: VideoProjectEntity,
        scenes: List<SceneEntity>,
        subtitles: List<SubtitleSegmentEntity>
    ): Int {
        val id = dao.insertProject(project).toInt()
        val populatedScenes = scenes.map { it.copy(projectId = id) }
        val populatedSubs = subtitles.map { it.copy(projectId = id) }
        dao.insertScenes(populatedScenes)
        dao.insertSubtitles(populatedSubs)
        return id
    }

    suspend fun updateProject(project: VideoProjectEntity) {
        dao.updateProject(project)
    }

    suspend fun deleteProject(id: Int) {
        dao.deleteProjectById(id)
    }

    suspend fun getScenesForProject(id: Int): List<SceneEntity> {
        return dao.getScenesByProjectIdList(id)
    }

    suspend fun getSubtitlesForProject(id: Int): List<SubtitleSegmentEntity> {
        return dao.getSubtitlesByProjectIdList(id)
    }

    suspend fun updateSubtitle(subtitle: SubtitleSegmentEntity) {
        dao.updateSubtitle(subtitle)
    }

    suspend fun saveSubtitles(projectId: Int, subtitles: List<SubtitleSegmentEntity>) {
        dao.deleteSubtitlesByProjectId(projectId)
        dao.insertSubtitles(subtitles)
    }
}
