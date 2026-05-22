package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class ResponseFormatText(
    @Json(name = "mimeType") val mimeType: String
)

@JsonClass(generateAdapter = true)
data class ResponseFormat(
    @Json(name = "text") val text: ResponseFormatText? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseFormat") val responseFormat: ResponseFormat? = null,
    @Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

// --- Moshi Models for AI structured responses ---

@JsonClass(generateAdapter = true)
data class AiGeneratedScene(
    @Json(name = "sequenceIndex") val sequenceIndex: Int,
    @Json(name = "visualCaption") val visualCaption: String,
    @Json(name = "aiImagePrompt") val aiImagePrompt: String,
    @Json(name = "narrationScript") val narrationScript: String,
    @Json(name = "durationMs") val durationMs: Int,
    @Json(name = "transitionEffect") val transitionEffect: String
)

@JsonClass(generateAdapter = true)
data class AiGeneratedSubtitle(
    @Json(name = "sceneSequenceIndex") val sceneSequenceIndex: Int,
    @Json(name = "startMs") val startMs: Long,
    @Json(name = "endMs") val endMs: Long,
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class AiGeneratedMusic(
    @Json(name = "genre") val genre: String, // "Retro Beats", "Lo-Fi Lounge", "Cinematic Orchestral", "Ambient Waves", "Techno Pulse"
    @Json(name = "tempo") val tempo: String, // "SLOW", "MEDIUM", "FAST"
    @Json(name = "synthNotes") val synthNotes: String // Note list like "C4,E4,G4,B4,C5"
)

@JsonClass(generateAdapter = true)
data class AiVideoResponse(
    @Json(name = "title") val title: String,
    @Json(name = "durationSeconds") val durationSeconds: Int,
    @Json(name = "music") val music: AiGeneratedMusic,
    @Json(name = "scenes") val scenes: List<AiGeneratedScene>,
    @Json(name = "subtitles") val subtitles: List<AiGeneratedSubtitle>
)

interface GeminiVideoService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiVideoService by lazy {
        val moshi = com.squareup.moshi.Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiVideoService::class.java)
    }
}
