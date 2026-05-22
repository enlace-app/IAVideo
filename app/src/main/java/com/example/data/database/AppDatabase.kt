package com.example.data.database

import android.content.Context
import androidx.room.*
import com.example.data.model.VideoProjectEntity
import com.example.data.model.SceneEntity
import com.example.data.model.SubtitleSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoProjectDao {
    @Query("SELECT * FROM video_projects ORDER BY createdTime DESC")
    fun getAllProjects(): Flow<List<VideoProjectEntity>>

    @Query("SELECT * FROM video_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Int): VideoProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: VideoProjectEntity): Long

    @Update
    suspend fun updateProject(project: VideoProjectEntity)

    @Query("DELETE FROM video_projects WHERE id = :id")
    suspend fun deleteProjectById(id: Int)

    // Scenes
    @Query("SELECT * FROM video_scenes WHERE projectId = :projectId ORDER BY sequenceIndex ASC")
    fun getScenesByProjectId(projectId: Int): Flow<List<SceneEntity>>

    @Query("SELECT * FROM video_scenes WHERE projectId = :projectId ORDER BY sequenceIndex ASC")
    suspend fun getScenesByProjectIdList(projectId: Int): List<SceneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenes(scenes: List<SceneEntity>)

    @Query("DELETE FROM video_scenes WHERE projectId = :projectId")
    suspend fun deleteScenesByProjectId(projectId: Int)

    // Subtitles
    @Query("SELECT * FROM subtitle_segments WHERE projectId = :projectId ORDER BY startMs ASC")
    fun getSubtitlesByProjectId(projectId: Int): Flow<List<SubtitleSegmentEntity>>

    @Query("SELECT * FROM subtitle_segments WHERE projectId = :projectId ORDER BY startMs ASC")
    suspend fun getSubtitlesByProjectIdList(projectId: Int): List<SubtitleSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitles(subtitles: List<SubtitleSegmentEntity>)

    @Update
    suspend fun updateSubtitle(subtitle: SubtitleSegmentEntity)

    @Query("DELETE FROM subtitle_segments WHERE id = :id")
    suspend fun deleteSubtitleById(id: Int)

    @Query("DELETE FROM subtitle_segments WHERE projectId = :projectId")
    suspend fun deleteSubtitlesByProjectId(projectId: Int)
}

@Database(
    entities = [VideoProjectEntity::class, SceneEntity::class, SubtitleSegmentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoProjectDao(): VideoProjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vidai_studio_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
