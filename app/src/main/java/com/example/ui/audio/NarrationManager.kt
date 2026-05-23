package com.example.ui.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * ✅ FASE 4: Narración real usando Android TextToSpeech (gratuito, sin API key)
 *
 * Uso básico:
 *   val narrator = NarrationManager(context)
 *   narrator.speak("Texto de la escena")
 *   narrator.stop()
 *   narrator.shutdown() // en onCleared()
 */
class NarrationManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null
    private var volume: Float = 0.8f

    // Callback opcional para saber cuándo termina de hablar una escena
    var onSpeakingFinished: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                configureVoice()
                // Si había texto pendiente mientras iniciaba, lo reproduce ahora
                pendingText?.let {
                    speakInternal(it)
                    pendingText = null
                }
            }
        }
    }

    /**
     * Configura el idioma y parámetros de voz.
     * Intenta español primero; si no está disponible, usa el idioma del sistema.
     */
    private fun configureVoice() {
        val ttsEngine = tts ?: return

        // Intentar español de España, luego genérico, luego sistema
        val spanishResult = ttsEngine.setLanguage(Locale("es", "ES"))
        if (spanishResult == TextToSpeech.LANG_MISSING_DATA ||
            spanishResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            val genericSpanish = ttsEngine.setLanguage(Locale("es"))
            if (genericSpanish == TextToSpeech.LANG_MISSING_DATA ||
                genericSpanish == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // Fallback al idioma del sistema
                ttsEngine.setLanguage(Locale.getDefault())
            }
        }

        // Velocidad y tono para sonido natural de narración de video
        ttsEngine.setSpeechRate(0.95f)   // Ligeramente más lento que normal: más claro
        ttsEngine.setPitch(1.05f)         // Ligeramente más agudo: más energético

        // Listener para notificar cuando termina de hablar
        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onSpeakingFinished?.invoke()
            }
            override fun onError(utteranceId: String?) {}
        })
    }

    /**
     * Narra el texto dado. Si el TTS aún no está listo, encola el texto.
     * Llama a esto cada vez que cambia la escena activa durante la reproducción.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (!isReady) {
            pendingText = text
            return
        }
        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        tts?.apply {
            stop() // Para cualquier narración anterior
            // QUEUE_FLUSH reemplaza lo que se esté reproduciendo con el nuevo texto
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "scene_narration")
        }
    }

    /** Ajusta el volumen de la voz (0.0 - 1.0) */
    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        // Android TTS no tiene control de volumen directo por API pública;
        // el volumen del stream MUSIC lo gestiona el sistema operativo.
        // El usuario puede usar el volumen del sistema para ajustarlo.
    }

    /** Detiene la narración en curso sin liberar el motor */
    fun stop() {
        tts?.stop()
    }

    /** Pausa la narración (Android TTS no soporta pausa real, equivale a stop) */
    fun pause() {
        stop()
    }

    /**
     * Libera los recursos del motor TTS.
     * IMPORTANTE: Llama a esto en onCleared() del ViewModel para evitar memory leaks.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
