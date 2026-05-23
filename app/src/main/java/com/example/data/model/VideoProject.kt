package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "video_projects")
data class VideoProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val prompt: String,
    val createdTime: Long = System.currentTimeMillis(),
    val backgroundMusic: String = "Retro Beats",
    val musicVolume: Float = 0.5f,
    val voiceVolume: Float = 0.8f,
    val subtitleColor: String = "#FFEB3B",
    val subtitleFamily: String = "IMPACT",
    val subtitleStyle: String = "OUTLINE",
    val subtitleLocation: String = "BOTTOM",
    val isExported: Boolean = false,
    val durationSeconds: Int = 30
)

@Entity(
    tableName = "video_scenes",
    foreignKeys = [
        ForeignKey(
            entity = VideoProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class SceneEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val sequenceIndex: Int,
    val visualCaption: String,
    val aiImagePrompt: String,
    val narrationScript: String,
    val durationMs: Int,
    val transitionEffect: String = "SLIDE",
    // ✅ NUEVO: URL de la imagen generada por Pollinations.ai
    // Se rellena automáticamente al crear el proyecto, null hasta que cargue
    val generatedImageUrl: String? = null
)

@Entity(
    tableName = "subtitle_segments",
    foreignKeys = [
        ForeignKey(
            entity = VideoProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class SubtitleSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val sceneSequenceIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class FullVideoProject(
    val project: VideoProjectEntity,
    val scenes: List<SceneEntity>,
    val subtitles: List<SubtitleSegmentEntity>
)
