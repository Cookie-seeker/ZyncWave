package com.example.zyncwave2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente para la API pública de lyrics.ovh.
 * Sin API key, sin registro, completamente gratuita.
 * Documentación: https://lyricsovh.docs.apiary.io
 *
 * Endpoint: GET https://api.lyrics.ovh/v1/{artist}/{title}
 * Retorna letras planas únicamente (sin timestamps LRC).
 */
object LyricsOvhApi {

    private const val BASE_URL    = "https://api.lyrics.ovh/v1"
    private const val TIMEOUT     = 15000
    private const val MAX_RETRY   = 3
    private const val RETRY_DELAY = 1500L

    data class LyricsOvhResult(
        val title: String,
        val artistName: String,
        val plainLyrics: String,
        val syncedLyrics: String? = null   // lyrics.ovh no soporta LRC
    )

    /**
     * Busca letras por artista y título.
     * lyrics.ovh no tiene endpoint de búsqueda — retorna directamente
     * las letras si las encuentra, o error si no existen.
     * Se retorna como lista de un solo elemento para mantener
     * compatibilidad con el modelo unificado de LyricsRepository.
     */
    suspend fun search(
        title: String,
        artist: String
    ): Result<List<LyricsOvhResult>> = withContext(Dispatchers.IO) {
        retry(MAX_RETRY) {
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val encodedTitle  = URLEncoder.encode(title,  "UTF-8")
            val url = URL("$BASE_URL/$encodedArtist/$encodedTitle")

            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT
                readTimeout    = TIMEOUT
                setRequestProperty("User-Agent", "ZyncWave2/1.0")
                requestMethod = "GET"
            }

            val code = conn.responseCode
            if (code == 404) throw Exception("No encontrado")
            if (code != 200) throw Exception("HTTP $code")

            val body   = conn.inputStream.bufferedReader().readText()
            val json   = JSONObject(body)
            val lyrics = json.optString("lyrics", "").trim()

            if (lyrics.isBlank()) throw Exception("Sin letras")

            listOf(
                LyricsOvhResult(
                    title      = title,
                    artistName = artist,
                    plainLyrics = lyrics
                )
            )
        }
    }

    /**
     * Ejecuta [block] hasta [times] veces con delay entre reintentos.
     */
    private suspend fun <T> retry(
        times: Int,
        block: suspend () -> T
    ): Result<T> {
        var lastError: Exception = Exception("Sin intentos")
        repeat(times) { attempt ->
            try {
                return Result.success(block())
            } catch (e: Exception) {
                lastError = e
                if (attempt < times - 1) delay(RETRY_DELAY)
            }
        }
        return Result.failure(lastError)
    }
}