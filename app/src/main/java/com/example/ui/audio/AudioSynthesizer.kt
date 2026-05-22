package com.example.ui.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.math.sin

class AudioSynthesizer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var synthJob: Job? = null
    private var volume = 0.5f

    // Frequencies mapping for retro synth chords & baselines
    private val noteFrequencies = mapOf(
        "C2" to 65.41, "D2" to 73.42, "E2" to 82.41, "F2" to 87.31, "G2" to 98.00, "A2" to 110.00, "B2" to 123.47,
        "C3" to 130.81, "D3" to 146.83, "E3" to 164.81, "F3" to 174.61, "G3" to 196.00, "A3" to 220.00, "B3" to 246.94,
        "C4" to 261.63, "D4" to 293.66, "E4" to 329.63, "F4" to 349.23, "G4" to 392.00, "A4" to 440.00, "B4" to 493.88,
        "C5" to 523.25, "D5" to 587.33, "E5" to 659.25, "F5" to 698.46, "G5" to 783.99, "A5" to 880.00, "B5" to 987.77
    )

    fun setVolume(vol: Float) {
        this.volume = vol.coerceIn(0f, 1f)
        try {
            synchronized(this) {
                audioTrack?.setVolume(this.volume)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startPlaying(notesString: String, tempo: String) {
        stopPlaying()
        isPlaying = true

        val notes = parseNotes(notesString)
        val noteDurationMs = when (tempo.uppercase()) {
            "SLOW" -> 2000L
            "FAST" -> 500L
            else -> 1000L // MEDIUM
        }

        synthJob = CoroutineScope(Dispatchers.Default).launch {
            var track: AudioTrack? = null
            val sampleRate = 22050 // 22kHz is plenty and reduces CPU significantly
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            try {
                // Deprecated constructor for high compatibility down to minSdk 24
                @Suppress("DEPRECATION")
                val tempTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2,
                    AudioTrack.MODE_STREAM
                )

                synchronized(this@AudioSynthesizer) {
                    if (isPlaying) {
                        audioTrack = tempTrack
                        track = tempTrack
                    } else {
                        try { tempTrack.release() } catch (e: Exception) {}
                    }
                }

                track?.apply {
                    setVolume(volume)
                    play()
                }

                var noteIndex = 0
                while (isPlaying && coroutineContext.isActive && track != null) {
                    val currentNote = if (notes.isNotEmpty()) notes[noteIndex % notes.size] else "C3"
                    val freq = noteFrequencies[currentNote] ?: 261.63

                    val durationSeconds = noteDurationMs / 1000.0
                    val numSamples = (durationSeconds * sampleRate).toInt()
                    val samples = ShortArray(numSamples)

                    for (i in 0 until numSamples) {
                        val t = i.toDouble() / sampleRate
                        
                        // Let's build a beautiful classic analog sound:
                        // Combining Fundamental scale frequency + sub-octave harmony + perfect fifth chord
                        val osc1 = sin(2.0 * Math.PI * freq * t)
                        val osc2 = sin(1.0 * Math.PI * freq * t) * 0.6 // Warm sub-octave
                        val osc3 = sin(3.0 * Math.PI * freq * 1.5 * t) * 0.35 // Atmospheric fifth
                        
                        // Attack-Sustain-Decay-Release envelope (prevent clicks when transitioning notes)
                        val envelope = if (i < numSamples * 0.15) {
                            i / (numSamples * 0.15) // Smooth attack
                        } else if (i > numSamples * 0.75) {
                            1.0 - (i - numSamples * 0.75) / (numSamples * 0.25) // Smooth release
                        } else {
                            1.0 // Sustain
                        }

                        // Combine oscillators and apply envelope
                        val waveSum = (osc1 + osc2 + osc3) / 1.95
                        val value = (waveSum * envelope * 32767.0).toInt()
                        samples[i] = value.coerceIn(-32768, 32767).toShort()
                    }

                    // Write synchronously inside background coroutine safely checking status
                    if (!isPlaying || !coroutineContext.isActive) break
                    track?.write(samples, 0, numSamples)
                    noteIndex++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    track?.apply {
                        if (state == AudioTrack.STATE_INITIALIZED) {
                            stop()
                            release()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                synchronized(this@AudioSynthesizer) {
                    if (audioTrack == track) {
                        audioTrack = null
                    }
                }
            }
        }
    }

    fun stopPlaying() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null
        synchronized(this) {
            audioTrack = null
        }
    }

    private fun parseNotes(notesString: String): List<String> {
        val clean = notesString.trim()
            .replace("[", "")
            .replace("]", "")
            .replace("\"", "")
        if (clean.isEmpty()) return listOf("C3", "E3", "G3", "B3", "C4")
        
        return clean.split(Regex("[,\\s]+"))
            .map { it.trim().uppercase() }
            .filter { noteFrequencies.containsKey(it) }
    }
}
