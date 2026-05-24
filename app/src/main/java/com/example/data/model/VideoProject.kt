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
    val backgroundMusic: String = "Retro Beats", // "Retro Beats", "Lo-Fi Lounge", "Cinematic Orchestral", "Ambient Waves", "Techno Pulse"
    val musicVolume: Float = 0.5f,
    val voiceVolume: Float = 0.8f,
    val subtitleColor: String = "#FFEB3B", // Material Yellow
    val subtitleFamily: String = "IMPACT", // "IMPACT", "DISPLAY", "MONO", "SANS"
    val subtitleStyle: String = "OUTLINE", // "OUTLINE", "BACKGROUND", "NONE"
    val subtitleLocation: String = "BOTTOM", // "TOP", "CENTER", "BOTTOM"
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
    val visualCaption: String, // Decrypted video summary scene description
    val aiImagePrompt: String, // Best prompt to describe the scene
    val narrationScript: String, // Text narrated by speaker during this scene
    val durationMs: Int,
    val transitionEffect: String = "SLIDE" // "FADE", "ZOOM", "SLIDE"
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
