package com.example.data.api

import java.net.URLEncoder

/**
 * Servicio de generación de imágenes usando Pollinations.ai
 *
 * ✅ 100% GRATUITO — Sin API key, sin registro, sin límites estrictos
 * Documentación: https://pollinations.ai
 *
 * Uso:
 *   val url = PollinationsImageService.buildImageUrl("cyberpunk sunset neon 8k")
 *   // → carga esa URL con Coil en un AsyncImage
 */
object PollinationsImageService {

    private const val BASE_URL = "https://image.pollinations.ai/prompt/"

    // Resolución de imagen para cada escena del video (16:9)
    private const val WIDTH = 1280
    private const val HEIGHT = 720

    /**
     * Construye la URL de imagen lista para usar con Coil/AsyncImage.
     *
     * @param prompt  El aiImagePrompt generado por Gemini para esta escena
     * @param seed    Semilla para reproducibilidad. Usa el sequenceIndex de la escena.
     * @param model   Modelo de imagen. "flux" es el más rápido y de mayor calidad.
     * @return        URL lista para cargar con AsyncImage o Glide
     */
    fun buildImageUrl(
        prompt: String,
        seed: Int = 42,
        model: String = "flux"
    ): String {
        // Pollinations necesita el prompt codificado en la URL
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
            .replace("+", "%20") // Pollinations prefiere %20 sobre +

        return "$BASE_URL$encodedPrompt" +
                "?width=$WIDTH" +
                "&height=$HEIGHT" +
                "&model=$model" +
                "&seed=$seed" +
                "&nologo=true" +    // Quita el logo de Pollinations de la imagen
                "&enhance=true"     // Mejora automática del prompt
    }

    /**
     * Añade estilo cinemático al prompt para mejorar la calidad visual.
     * Gemini genera prompts buenos pero sin suficientes keywords de calidad.
     */
    fun enhancePrompt(originalPrompt: String): String {
        // Evitamos añadir keywords duplicadas si Gemini ya las incluyó
        val keywords = listOf("8k", "cinematic", "photorealistic", "sharp focus")
        val promptLower = originalPrompt.lowercase()
        val missing = keywords.filter { it !in promptLower }

        return if (missing.isEmpty()) {
            originalPrompt
        } else {
            "$originalPrompt, ${missing.joinToString(", ")}"
        }
    }

    /**
     * Genera la URL final lista para usar, combinando enhance + build.
     * Este es el método principal que debes llamar desde el ViewModel.
     *
     * Ejemplo:
     *   val url = PollinationsImageService.getImageUrl(
     *       prompt = scene.aiImagePrompt,
     *       seed = scene.sequenceIndex
     *   )
     */
    fun getImageUrl(prompt: String, seed: Int = 42): String {
        val enhanced = enhancePrompt(prompt)
        return buildImageUrl(enhanced, seed)
    }
}
