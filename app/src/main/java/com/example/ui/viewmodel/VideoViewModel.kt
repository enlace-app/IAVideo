package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.AiVideoResponse
import com.example.data.api.GenerateContentRequest
import com.example.data.api.Content
import com.example.data.api.Part
import com.example.data.api.GenerationConfig
import com.example.data.api.ResponseFormat
import com.example.data.api.ResponseFormatText
import com.example.data.api.RetrofitClient
import com.example.data.database.AppDatabase
import com.example.data.model.FullVideoProject
import com.example.data.model.VideoProjectEntity
import com.example.data.model.SceneEntity
import com.example.data.model.SubtitleSegmentEntity
import com.example.data.repository.VideoRepository
import com.example.ui.audio.AudioSynthesizer
import com.example.ui.audio.NarrationManager
import com.example.ui.video.VideoExporter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class VideoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = VideoRepository(database.videoProjectDao())
    private val synthesizer = AudioSynthesizer()
    // ✅ FASE 4: Motor de narración con Android TTS
    private val narrationManager = NarrationManager(application)
    // ✅ FASE 5: Exportador real de video MP4
    private val videoExporter = VideoExporter(application)

    // Public list of all saved projects
    val allProjects: StateFlow<List<VideoProjectEntity>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Editor & Interface State
    val currentProject = MutableStateFlow<FullVideoProject?>(null)
    val isGenerating = MutableStateFlow(false)
    val generationStatus = MutableStateFlow("")
    val isApiKeyMissing = MutableStateFlow(BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY")
    val generationError = MutableStateFlow<String?>(null)

    // Playback Timeline States
    val isPlaying = MutableStateFlow(false)
    val playbackProgressMs = MutableStateFlow(0L)
    val activeSceneIndex = MutableStateFlow(0)
    val activeSubtitleText = MutableStateFlow("")

    // Rendering Export States
    val isExporting = MutableStateFlow(false)
    val exportProgress = MutableStateFlow(0f)
    val exportStep = MutableStateFlow("")

    private var playbackJob: Job? = null

    init {
        // Observe playback state to sync synthesizer
        viewModelScope.launch {
            isPlaying.collect { playing ->
                val proj = currentProject.value
                if (playing && proj != null) {
                    synthesizer.setVolume(proj.project.musicVolume)
                    narrationManager.setVolume(proj.project.voiceVolume)
                    val notes = proj.project.backgroundMusic.let { getNotesForGenre(it) }
                    val tempo = when (proj.project.backgroundMusic) {
                        "Techno Pulse" -> "FAST"
                        "Ambient Waves" -> "SLOW"
                        else -> "MEDIUM"
                    }
                    synthesizer.startPlaying(notes, tempo)
                    // ✅ Narrar la escena activa al comenzar reproducción
                    val activeScene = proj.scenes.getOrNull(activeSceneIndex.value)
                    if (activeScene != null && activeScene.narrationScript.isNotBlank()) {
                        narrationManager.speak(activeScene.narrationScript)
                    }
                    startPlaybackTicker()
                } else {
                    synthesizer.stopPlaying()
                    narrationManager.pause()
                    stopPlaybackTicker()
                }
            }
        }
    }

    private fun getNotesForGenre(genre: String): String {
        return when (genre) {
            "Retro Beats" -> "C3 E3 G3 A3 C4 E4 G4 A4"
            "Lo-Fi Lounge" -> "C3 G3 B3 D4 E4 G4 B4 D5"
            "Cinematic Orchestral" -> "A2 E3 A3 C4 E4 A4 B4 C5"
            "Ambient Waves" -> "F2 C3 F3 A3 C4 F4 A4 B4"
            "Techno Pulse" -> "E2 E3 G2 G3 A2 A3 B2 B3"
            else -> "C3 E3 G3 B3 C4"
        }
    }

    fun play() {
        if (currentProject.value != null) {
            isPlaying.value = true
        }
    }

    fun pause() {
        isPlaying.value = false
    }

    fun seekTo(progressMs: Long) {
        val proj = currentProject.value ?: return
        val totalDurationMs = proj.scenes.sumOf { it.durationMs }
        playbackProgressMs.value = progressMs.coerceIn(0L, totalDurationMs.toLong())
        updateTimelineIndexAndSubtitle()
    }

    fun selectProject(project: VideoProjectEntity) {
        viewModelScope.launch {
            pause()
            val full = repository.getFullProject(project.id)
            currentProject.value = full
            playbackProgressMs.value = 0L
            activeSceneIndex.value = 0
            activeSubtitleText.value = ""
            if (full != null) {
                synthesizer.setVolume(full.project.musicVolume)
            }
        }
    }

    fun closeProject() {
        pause()
        currentProject.value = null
    }

    fun deleteProject(id: Int) {
        viewModelScope.launch {
            if (currentProject.value?.project?.id == id) {
                closeProject()
            }
            repository.deleteProject(id)
        }
    }

    // Tickers to update subtitle overlays and active scenes in real-time
    private fun startPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val totalDurationMs = currentProject.value?.scenes?.sumOf { it.durationMs } ?: 0
            while (isActive && isPlaying.value) {
                delay(30)
                val nextProgress = playbackProgressMs.value + 30
                if (nextProgress >= totalDurationMs) {
                    playbackProgressMs.value = 0L
                    isPlaying.value = false // Auto loop or stop
                } else {
                    playbackProgressMs.value = nextProgress
                }
                updateTimelineIndexAndSubtitle()
            }
        }
    }

    private fun stopPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun updateTimelineIndexAndSubtitle() {
        val proj = currentProject.value ?: return
        val progress = playbackProgressMs.value

        // Determine active scene based on accumulated duration
        var accumMs = 0L
        var foundSceneIdx = 0
        for ((idx, scene) in proj.scenes.withIndex()) {
            if (progress >= accumMs && progress < accumMs + scene.durationMs) {
                foundSceneIdx = idx
                break
            }
            accumMs += scene.durationMs
            foundSceneIdx = idx
        }
        // ✅ Si cambia la escena, narrar el nuevo script
        val previousIdx = activeSceneIndex.value
        activeSceneIndex.value = foundSceneIdx
        if (foundSceneIdx != previousIdx && isPlaying.value) {
            val scene = proj.scenes.getOrNull(foundSceneIdx)
            if (scene != null && scene.narrationScript.isNotBlank()) {
                narrationManager.speak(scene.narrationScript)
            }
        }

        // Determine active subtitle segment
        val activeSub = proj.subtitles.firstOrNull { sub ->
            progress >= sub.startMs && progress < sub.endMs
        }
        activeSubtitleText.value = activeSub?.text ?: ""
    }

    // Dynamic Volume Controllers
    fun updateMusicVolume(vol: Float) {
        val full = currentProject.value ?: return
        viewModelScope.launch {
            val updatedProj = full.project.copy(musicVolume = vol)
            repository.updateProject(updatedProj)
            currentProject.value = full.copy(project = updatedProj)
            synthesizer.setVolume(vol)
        }
    }

    fun updateVoiceVolume(vol: Float) {
        val full = currentProject.value ?: return
        viewModelScope.launch {
            val updatedProj = full.project.copy(voiceVolume = vol)
            repository.updateProject(updatedProj)
            currentProject.value = full.copy(project = updatedProj)
        }
    }

    // Background Music Changer
    fun changeBackgroundMusic(genre: String) {
        val full = currentProject.value ?: return
        viewModelScope.launch {
            val updatedProj = full.project.copy(backgroundMusic = genre)
            repository.updateProject(updatedProj)
            currentProject.value = full.copy(project = updatedProj)
            if (isPlaying.value) {
                val notes = getNotesForGenre(genre)
                val tempo = if (genre == "Techno Pulse") "FAST" else "MEDIUM"
                synthesizer.startPlaying(notes, tempo)
            }
        }
    }

    // Subtitle Customization Engines
    fun updateSubtitleStyles(family: String, color: String, style: String, location: String) {
        val full = currentProject.value ?: return
        viewModelScope.launch {
            val updatedProj = full.project.copy(
                subtitleFamily = family,
                subtitleColor = color,
                subtitleStyle = style,
                subtitleLocation = location
            )
            repository.updateProject(updatedProj)
            currentProject.value = full.copy(project = updatedProj)
        }
    }

    // In-Timeline Subtitle Edits (Auto Save)
    fun editSubtitleText(subId: Int, newText: String) {
        val full = currentProject.value ?: return
        viewModelScope.launch {
            val updatedList = full.subtitles.map {
                if (it.id == subId) it.copy(text = newText) else it
            }
            val target = updatedList.firstOrNull { it.id == subId }
            if (target != null) {
                repository.updateSubtitle(target)
                currentProject.value = full.copy(subtitles = updatedList)
                updateTimelineIndexAndSubtitle()
            }
        }
    }

    fun updateSubtitleTimes(subId: Int, startMs: Long, endMs: Long) {
        val full = currentProject.value ?: return
        viewModelScope.launch {
            val updatedList = full.subtitles.map {
                if (it.id == subId) it.copy(startMs = startMs, endMs = endMs) else it
            }
            val target = updatedList.firstOrNull { it.id == subId }
            if (target != null) {
                repository.updateSubtitle(target)
                currentProject.value = full.copy(subtitles = updatedList)
                updateTimelineIndexAndSubtitle()
            }
        }
    }

    // AI Long Video Generator Engine (Gemini REST Call)
    fun generateAIProject(userPrompt: String) {
        if (isApiKeyMissing.value) {
            generationError.value = "Api key no configurada. Configure su API_KEY en el panel de Secrets de AI Studio."
            return
        }

        isGenerating.value = true
        generationError.value = null
        generationStatus.value = "Iniciando VidAI engine..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Brainstorm Script, Scenes and Soundtrack parameters in JSON format
                updateStatus("Invocando a la Inteligencia Artificial (Gemini 3.5)...")
                
                val apiRequest = createGeminiRequest(userPrompt)
                val rawResponse = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, apiRequest)
                
                updateStatus("Procesando datos cinematográficos generados...")
                val responseText = rawResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("No se recibió respuesta válida del modelo de IA.")

                // Parse response with Moshi
                updateStatus("Analizando y codificando guiones, música y subtítulos...")
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(AiVideoResponse::class.java)
                
                val parsedData = try {
                    adapter.fromJson(responseText) ?: throw Exception("JSON mal formateado.")
                } catch (e: Exception) {
                    // Try repairing common bracket errors from direct strings
                    val trimmedText = cleanMarkdownJson(responseText)
                    adapter.fromJson(trimmedText) ?: throw Exception("Error al parsear el JSON de respuesta. Intente de nuevo.\n$responseText")
                }

                updateStatus("Sintetizando escenas y editando subtítulos automáticos...")
                
                // Construct models
                val projectEntity = VideoProjectEntity(
                    title = parsedData.title,
                    prompt = userPrompt,
                    backgroundMusic = parsedData.music.genre,
                    durationSeconds = parsedData.durationSeconds,
                    musicVolume = 0.5f,
                    voiceVolume = 0.8f
                )

                val scenesList = parsedData.scenes.map {
                    SceneEntity(
                        projectId = 0, // Assigned inside Repository
                        sequenceIndex = it.sequenceIndex,
                        visualCaption = it.visualCaption,
                        aiImagePrompt = it.aiImagePrompt,
                        narrationScript = it.narrationScript,
                        durationMs = it.durationMs,
                        transitionEffect = it.transitionEffect
                    )
                }

                val subsList = parsedData.subtitles.map {
                    SubtitleSegmentEntity(
                        projectId = 0,
                        sceneSequenceIndex = it.sceneSequenceIndex,
                        startMs = it.startMs,
                        endMs = it.endMs,
                        text = it.text
                    )
                }

                // Save to Room for offline persistence
                updateStatus("Guardando proyecto de video en base de datos local...")
                val newId = repository.createProject(projectEntity, scenesList, subsList)
                
                withContext(Dispatchers.Main) {
                    val savedProject = repository.getFullProject(newId)
                    currentProject.value = savedProject
                    isGenerating.value = false
                    generationStatus.value = ""
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    generationError.value = "Error de generación: ${e.localizedMessage}"
                    isGenerating.value = false
                }
            }
        }
    }

    private fun cleanMarkdownJson(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.substringAfter("```").substringBeforeLast("```").trim()
        }
        return clean
    }

    private fun updateStatus(status: String) {
        viewModelScope.launch(Dispatchers.Main) {
            generationStatus.value = status
        }
    }

    private fun createGeminiRequest(userPrompt: String): GenerateContentRequest {
        val systemInstruction = """
            Eres un productor experto en creación de videos largos AI llamado "VidAI Engine".
            Tu tarea es recibir un tema o prompt sobre un video y transformarlo en un proyecto completo que incluye un guion cinematográfico coherente, escenas, timmings exactos para un video largo (aprox 30-45 segundos divididos en escenas), recomendación de música y subtítulos perfectamente sincronizados.
            
            Debes responder UNICAMENTE con un objeto JSON válido que cumpla con este formato exacto de Moshi. No agregues formatos decorativos extras fuera del bloque JSON.
            
            Formato de Entrada: El usuario te provee un tema de video en español.
            Formato de Salida Requerido (JSON):
            {
              "title": "Título llamativo para el video",
              "durationSeconds": 30,
              "music": {
                "genre": "Retro Beats",  // Elije uno de: "Retro Beats", "Lo-Fi Lounge", "Cinematic Orchestral", "Ambient Waves", "Techno Pulse"
                "tempo": "MEDIUM",       // "SLOW", "MEDIUM", "FAST"
                "synthNotes": "C3,E3,G3,B3,C4"  // Una lista de notas de música sintetizable representadas con notas clásicas separadas por comas.
              },
              "scenes": [
                {
                  "sequenceIndex": 0,
                  "visualCaption": "Descripción visual de lo que se muestra en pantalla para esta parte del video (ej. Puesta de sol futurista con destellos dorados)",
                  "aiImagePrompt": "Prompt en inglés altamente estilizado de Stable Diffusion/Midjourney para generar esta ilustración de escena (ej: cyberpunk futuristic sunset, neon pink highlights, golden hour, 8k, concept art)",
                  "narrationScript": "La narración que una voz con IA dirá en esta escena. Máximo 15 palabras por escena.",
                  "durationMs": 10000,     // Duración de la escena en milisegundos (ej: 10000 es 10 segundos).
                  "transitionEffect": "SLIDE" // Efecto de transición: "SLIDE", "FADE" o "ZOOM"
                },
                ... (crea al menos 3 a 4 escenas secuenciales para cubrir los 30-40 segundos)
              ],
              "subtitles": [
                {
                  "sceneSequenceIndex": 0,
                  "startMs": 0,            // Tiempo de inicio absoluto en el video en milisegundos
                  "endMs": 3000,           // Tiempo de fin absoluto en milisegundos
                  "text": "Frase de subtítulo para este fragmento de narración"
                },
                {
                  "sceneSequenceIndex": 0,
                  "startMs": 3100,
                  "endMs": 9500,
                  "text": "Siguiente frase de subtítulo sync"
                }
                ... (Genera de 2 a 3 segmentos de subtítulos cortos para cada escena, de forma que cubran y correspondan exactamente al 'narrationScript' de su respectiva escena).
              ]
            }

            Requisitos estrictos de sincronización de subtítulos:
            - Cada escena dura un rango exacto de tiempo. Los startMs y endMs de los subtítulos de una escena deben caer estrictamente dentro de los tiempos de esa escena.
            - Ejemplo: Si la Escena 0 va de 0 a 10000ms, y la Escena 1 va de 10000ms a 20000ms:
              - Los subtítulos de la Escena 0 deben tener startMs y endMs entre 0 y 10000ms.
              - Los subtítulos de la Escena 1 deben tener startMs y endMs entre 10000ms y 20000ms.
            - Los subtítulos deben estar en español, deben ser frases de 3-5 palabras, asombrosamente sincronizados y legibles.
            - No repitas el formato. Responde solo con JSON puro.
        """.trimIndent()

        val content = Content(parts = listOf(Part(text = "Tema de video: $userPrompt")))
        val systemInst = Content(parts = listOf(Part(text = systemInstruction)))

        return GenerateContentRequest(
            contents = listOf(content),
            generationConfig = GenerationConfig(
                responseFormat = ResponseFormat(text = ResponseFormatText(mimeType = "application/json")),
                temperature = 0.7f
            ),
            systemInstruction = systemInst
        )
    }

    // ✅ FASE 5: Exportación real de video MP4 con MediaCodec + MediaMuxer
    fun renderAndExportVideo(onCompleted: () -> Unit) {
        val full = currentProject.value ?: return
        pause()
        isExporting.value = true
        exportProgress.value = 0f
        exportStep.value = "Preparando exportación..."

        viewModelScope.launch {
            val savedPath = videoExporter.export(full) { progress ->
                exportStep.value = progress.step
                exportProgress.value = progress.progress
            }

            // Actualizar estado en base de datos
            val updatedProj = full.project.copy(isExported = savedPath != null)
            repository.updateProject(updatedProj)
            currentProject.value = full.copy(project = updatedProj)

            exportStep.value = if (savedPath != null) {
                "¡Video guardado en la Galería!"
            } else {
                "Error al exportar. Comprueba tu conexión e inténtalo de nuevo."
            }

            kotlinx.coroutines.delay(1500)
            isExporting.value = false
            onCompleted()
        }
    }

    override fun onCleared() {
        super.onCleared()
        synthesizer.stopPlaying()
        narrationManager.shutdown() // ✅ Liberar motor TTS
        stopPlaybackTicker()
    }
}
