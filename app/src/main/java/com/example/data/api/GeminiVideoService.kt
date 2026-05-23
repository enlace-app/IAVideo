package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
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
    @Json(name = "genre") val genre: String,
    @Json(name = "tempo") val tempo: String,
    @Json(name = "synthNotes") val synthNotes: String
)

@JsonClass(generateAdapter = true)
data class AiVideoResponse(
    @Json(name = "title") val title: String,
    @Json(name = "durationSeconds") val durationSeconds: Int,
    @Json(name = "music") val music: AiGeneratedMusic,
    @Json(name = "scenes") val scenes: List<AiGeneratedScene>,
    @Json(name = "subtitles") val subtitles: List<AiGeneratedSubtitle>
)

// Sealed class para manejar resultados de red con claridad
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val exception: Exception) : ApiResult<Nothing>()
}

interface GeminiVideoService {
    // ✅ Corregido: modelo real disponible en la API de Google
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): Response<GenerateContentResponse>
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
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

    // Función helper para ejecutar llamadas con manejo de errores automático
    suspend fun <T> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(response.code(), "Respuesta vacía del servidor")
                }
            } else {
                val errorMsg = when (response.code()) {
                    400 -> "Solicitud inválida. Revisa el prompt."
                    401 -> "API Key inválida. Ve a Ajustes y comprueba tu clave Gemini."
                    403 -> "Acceso denegado. Verifica los permisos de tu API Key."
                    429 -> "Límite de peticiones alcanzado. Espera un momento e intenta de nuevo."
                    500, 503 -> "Error en los servidores de Google. Intenta más tarde."
                    else -> "Error ${response.code()}: ${response.message()}"
                }
                ApiResult.Error(response.code(), errorMsg)
            }
        } catch (e: java.net.UnknownHostException) {
            ApiResult.NetworkError(Exception("Sin conexión a internet. Comprueba tu red."))
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.NetworkError(Exception("Tiempo de espera agotado. La red es lenta, intenta de nuevo."))
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }
}
