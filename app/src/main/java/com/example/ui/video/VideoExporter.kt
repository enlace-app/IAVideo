package com.example.ui.video

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.media.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.FullVideoProject
import com.example.data.model.SceneEntity
import com.example.data.model.SubtitleSegmentEntity
import com.example.data.model.VideoProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * ✅ FASE 5: Exportación real de video MP4
 *
 * Usa MediaCodec (encoder H.264) + MediaMuxer (contenedor MP4) — ambos nativos de Android.
 * Sin librerías externas. Sin coste. Funciona desde Android 7 (minSdk 24).
 *
 * Flujo:
 *  1. Descarga imágenes de Pollinations para cada escena
 *  2. Renderiza cada imagen como frames de video con Canvas (subtítulos incluidos)
 *  3. Codifica los frames en H.264 con MediaCodec
 *  4. Empaqueta en MP4 con MediaMuxer
 *  5. Guarda en la Galería del dispositivo con MediaStore
 */
class VideoExporter(private val context: Context) {

    companion object {
        const val VIDEO_WIDTH = 1280
        const val VIDEO_HEIGHT = 720
        const val VIDEO_FPS = 24
        const val VIDEO_BITRATE = 4_000_000 // 4 Mbps — buena calidad
        const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
    }

    data class ExportProgress(
        val step: String,
        val progress: Float  // 0.0 - 1.0
    )

    /**
     * Exporta el proyecto completo a un archivo MP4 en la Galería.
     *
     * @param project   Proyecto completo con escenas y subtítulos
     * @param onProgress Callback llamado con el progreso (0.0–1.0) y descripción del paso
     * @return           Ruta del archivo guardado, o null si hubo error
     */
    suspend fun export(
        project: FullVideoProject,
        onProgress: (ExportProgress) -> Unit
    ): String? = withContext(Dispatchers.IO) {

        val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")

        try {
            // PASO 1: Descargar imágenes de todas las escenas
            onProgress(ExportProgress("Descargando imágenes de las escenas...", 0.05f))
            val sceneBitmaps = downloadSceneImages(project.scenes, onProgress)

            // PASO 2: Configurar encoder H.264
            onProgress(ExportProgress("Configurando encoder de video H.264...", 0.25f))
            val encoder = setupEncoder()
            val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()

            encoder.start()

            // PASO 3: Codificar frames escena por escena
            var currentTimeUs = 0L
            val totalScenes = project.scenes.size

            for ((sceneIdx, scene) in project.scenes.withIndex()) {
                if (!isActive) break

                val progressBase = 0.25f + (sceneIdx.toFloat() / totalScenes) * 0.55f
                onProgress(ExportProgress(
                    "Renderizando escena ${sceneIdx + 1} de $totalScenes...",
                    progressBase
                ))

                val bitmap = sceneBitmaps[sceneIdx] ?: createFallbackBitmap(scene)
                val sceneDurationUs = scene.durationMs * 1000L
                val frameCount = (scene.durationMs / 1000f * VIDEO_FPS).toInt().coerceAtLeast(1)
                val frameDurationUs = sceneDurationUs / frameCount

                for (frameIdx in 0 until frameCount) {
                    if (!isActive) break

                    val frameTimeUs = currentTimeUs + frameIdx * frameDurationUs

                    // Renderizar frame con subtítulos si corresponde
                    val activeSubtitle = project.subtitles.firstOrNull { sub ->
                        val frameMs = frameTimeUs / 1000
                        frameMs >= sub.startMs && frameMs < sub.endMs
                    }

                    val frameBitmap = renderFrame(
                        sourceBitmap = bitmap,
                        scene = scene,
                        projectConfig = project.project,
                        subtitle = activeSubtitle?.text ?: "",
                        frameIndex = frameIdx,
                        totalFrames = frameCount
                    )

                    // Enviar frame al encoder
                    encodeFrame(encoder, frameBitmap, frameTimeUs)
                    frameBitmap.recycle()

                    // Drenar el encoder y escribir en el muxer
                    drainEncoder(encoder, muxer, bufferInfo, false) { trackIdx ->
                        if (videoTrackIndex == -1) {
                            videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                        }
                        videoTrackIndex
                    }
                }

                currentTimeUs += sceneDurationUs
                bitmap.recycle()
            }

            // PASO 4: Finalizar encoder y muxer
            onProgress(ExportProgress("Finalizando archivo MP4...", 0.85f))
            drainEncoder(encoder, muxer, bufferInfo, true) { videoTrackIndex }
            encoder.stop()
            encoder.release()
            if (videoTrackIndex != -1) muxer.stop()
            muxer.release()

            // PASO 5: Guardar en Galería con MediaStore
            onProgress(ExportProgress("Guardando en la Galería...", 0.92f))
            val savedPath = saveToGallery(tempFile, project.project.title)

            onProgress(ExportProgress("¡Video exportado correctamente!", 1.0f))
            savedPath

        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tempFile.delete()
        }
    }

    // -------------------------------------------------------------------------
    // Descarga de imágenes
    // -------------------------------------------------------------------------

    private suspend fun downloadSceneImages(
        scenes: List<SceneEntity>,
        onProgress: (ExportProgress) -> Unit
    ): Map<Int, Bitmap?> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<Int, Bitmap?>()
        scenes.forEachIndexed { idx, scene ->
            onProgress(ExportProgress(
                "Descargando imagen ${idx + 1} de ${scenes.size}...",
                0.05f + (idx.toFloat() / scenes.size) * 0.18f
            ))
            result[idx] = try {
                val imageUrl = com.example.data.api.PollinationsImageService.getImageUrl(
                    prompt = scene.aiImagePrompt,
                    seed = scene.sequenceIndex
                )
                val connection = URL(imageUrl).openConnection().apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                connection.getInputStream().use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null // Usará fallback de color sólido
            }
        }
        result
    }

    // -------------------------------------------------------------------------
    // Fallback cuando falla la descarga de imagen
    // -------------------------------------------------------------------------

    private fun createFallbackBitmap(scene: SceneEntity): Bitmap {
        val bmp = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colors = listOf(0xFF1E1B4B, 0xFF581C87, 0xFF0F172A, 0xFF831843)
        canvas.drawColor(colors[scene.sequenceIndex % colors.size].toInt())
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(scene.visualCaption, VIDEO_WIDTH / 2f, VIDEO_HEIGHT / 2f, paint)
        return bmp
    }

    // -------------------------------------------------------------------------
    // Renderizado de frame con subtítulos y transición
    // -------------------------------------------------------------------------

    private fun renderFrame(
        sourceBitmap: Bitmap,
        scene: SceneEntity,
        projectConfig: VideoProjectEntity,
        subtitle: String,
        frameIndex: Int,
        totalFrames: Int
    ): Bitmap {
        val frame = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)

        // Escalar imagen al tamaño del video manteniendo proporción
        val scaled = Bitmap.createScaledBitmap(sourceBitmap, VIDEO_WIDTH, VIDEO_HEIGHT, true)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        if (scaled != sourceBitmap) scaled.recycle()

        // Efecto de transición FADE en los primeros y últimos frames
        if (scene.transitionEffect == "FADE") {
            val fadeFrames = (totalFrames * 0.15f).toInt().coerceAtLeast(1)
            val alpha = when {
                frameIndex < fadeFrames -> (frameIndex.toFloat() / fadeFrames * 255).toInt()
                frameIndex > totalFrames - fadeFrames ->
                    ((totalFrames - frameIndex).toFloat() / fadeFrames * 255).toInt()
                else -> 255
            }
            if (alpha < 255) {
                val overlayPaint = Paint().apply { color = Color.argb(255 - alpha, 0, 0, 0) }
                canvas.drawRect(0f, 0f, VIDEO_WIDTH.toFloat(), VIDEO_HEIGHT.toFloat(), overlayPaint)
            }
        }

        // Renderizar subtítulos si hay texto activo
        if (subtitle.isNotBlank()) {
            drawSubtitles(canvas, subtitle, projectConfig)
        }

        return frame
    }

    private fun drawSubtitles(canvas: Canvas, text: String, config: VideoProjectEntity) {
        val textColor = try {
            Color.parseColor(config.subtitleColor)
        } catch (e: Exception) {
            Color.YELLOW
        }

        val textSize = 56f
        val padding = 20f

        val textPaint = Paint().apply {
            color = textColor
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }

        val yPos = when (config.subtitleLocation) {
            "TOP" -> textSize + padding * 2
            "CENTER" -> VIDEO_HEIGHT / 2f
            else -> VIDEO_HEIGHT - textSize - padding * 2  // BOTTOM
        }

        when (config.subtitleStyle) {
            "BACKGROUND" -> {
                val bgPaint = Paint().apply {
                    color = Color.argb(180, 0, 0, 0)
                }
                val textWidth = textPaint.measureText(text)
                canvas.drawRoundRect(
                    VIDEO_WIDTH / 2f - textWidth / 2f - padding,
                    yPos - textSize,
                    VIDEO_WIDTH / 2f + textWidth / 2f + padding,
                    yPos + padding,
                    16f, 16f, bgPaint
                )
                canvas.drawText(text, VIDEO_WIDTH / 2f, yPos, textPaint)
            }
            "OUTLINE" -> {
                val strokePaint = Paint(textPaint).apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
                canvas.drawText(text, VIDEO_WIDTH / 2f, yPos, strokePaint)
                canvas.drawText(text, VIDEO_WIDTH / 2f, yPos, textPaint)
            }
            else -> {
                canvas.drawText(text, VIDEO_WIDTH / 2f, yPos, textPaint)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Encoder H.264
    // -------------------------------------------------------------------------

    private fun setupEncoder(): MediaCodec {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        return MediaCodec.createEncoderByType(MIME_TYPE).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private fun encodeFrame(encoder: MediaCodec, bitmap: Bitmap, timeUs: Long) {
        val inputBufferIdx = encoder.dequeueInputBuffer(10_000L)
        if (inputBufferIdx < 0) return

        val inputBuffer = encoder.getInputBuffer(inputBufferIdx) ?: return
        inputBuffer.clear()

        val yuvData = bitmapToYUV420(bitmap)
        inputBuffer.put(yuvData)

        encoder.queueInputBuffer(inputBufferIdx, 0, yuvData.size, timeUs, 0)
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        getTrackIndex: (MediaFormat) -> Int
    ) {
        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }

        var keepDraining = true
        while (keepDraining) {
            val outputBufferIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            when {
                outputBufferIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) keepDraining = false
                }
                outputBufferIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Track se añade en el callback getTrackIndex
                    getTrackIndex(encoder.outputFormat)
                }
                outputBufferIdx >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIdx) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0) {
                        val trackIdx = getTrackIndex(encoder.outputFormat)
                        if (trackIdx >= 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIdx, outputBuffer, bufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(outputBufferIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        keepDraining = false
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Conversión Bitmap → YUV420 (formato que acepta el encoder H.264)
    // -------------------------------------------------------------------------

    private fun bitmapToYUV420(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val yuvSize = width * height * 3 / 2
        val yuv = ByteArray(yuvSize)

        var yIdx = 0
        var uvIdx = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = pixels[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIdx++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIdx++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIdx++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    // -------------------------------------------------------------------------
    // Guardar en Galería con MediaStore (compatible Android 7–15)
    // -------------------------------------------------------------------------

    private fun saveToGallery(tempFile: File, title: String): String? {
        val fileName = "IAVideo_${System.currentTimeMillis()}.mp4"
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]"), "")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ → MediaStore sin permiso de escritura
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/IAVideo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
                put(MediaStore.Video.Media.TITLE, safeTitle)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().copyTo(out)
            }

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri.toString()

        } else {
            // Android 7–9 → Guardar directamente en Movies/IAVideo
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "IAVideo"
            ).also { it.mkdirs() }
            val outFile = File(moviesDir, fileName)
            tempFile.copyTo(outFile, overwrite = true)
            outFile.absolutePath
        }
    }
}
