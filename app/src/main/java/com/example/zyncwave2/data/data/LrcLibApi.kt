package com.example.zyncwave2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente para la API pública de lrclib.net.
 * Documentación: https://lrclib.net/docs
 */
object LrcLibApi {

    private const val BASE_URL    = "https://lrclib.net/api"
    private const val TIMEOUT     = 15000
    private const val MAX_RETRY   = 3
    private const val RETRY_DELAY = 1500L

    data class LrcLibResult(
        val id: Int,
        val title: String,
        val artistName: String,
        val albumName: String,
        val duration: Double,
        val plainLyrics: String?,
        val syncedLyrics: String?
    )

    /**
     * Busca letras por título y artista.
     * Reintenta hasta MAX_RETRY veces ante fallos de red.
     */
    suspend fun search(
        title: String,
        artist: String
    ): Result<List<LrcLibResult>> = withContext(Dispatchers.IO) {
        retry(MAX_RETRY) {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url   = URL("$BASE_URL/search?q=$query")
            val conn  = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT
                readTimeout    = TIMEOUT
                setRequestProperty(
                    "Lrclib-Client",
                    "ZyncWave2 (github.com/Cookie-seeker/ZyncWave)"
                )
                requestMethod = "GET"
            }

            val code = conn.responseCode
            if (code != 200) throw Exception("HTTP $code")

            val body    = conn.inputStream.bufferedReader().readText()
            val array   = JSONArray(body)
            val results = mutableListOf<LrcLibResult>()

            for (i in 0 until array.length()) {
                results.add(array.getJSONObject(i).toResult())
            }
            results
        }
    }

    /**
     * Obtiene letras por ID exacto.
     * Reintenta hasta MAX_RETRY veces.
     */
    suspend fun getById(id: Int): Result<LrcLibResult> = withContext(Dispatchers.IO) {
        retry(MAX_RETRY) {
            val url  = URL("$BASE_URL/get/$id")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT
                readTimeout    = TIMEOUT
                setRequestProperty(
                    "Lrclib-Client",
                    "ZyncWave2 (github.com/Cookie-seeker/ZyncWave)"
                )
                requestMethod = "GET"
            }

            val code = conn.responseCode
            if (code != 200) throw Exception("HTTP $code")

            JSONObject(conn.inputStream.bufferedReader().readText()).toResult()
        }
    }

    private fun JSONObject.toResult() = LrcLibResult(
        id           = optInt("id", 0),
        title        = optString("trackName", ""),
        artistName   = optString("artistName", ""),
        albumName    = optString("albumName", ""),
        duration     = optDouble("duration", 0.0),
        plainLyrics  = optString("plainLyrics").takeIf  { it.isNotBlank() },
        syncedLyrics = optString("syncedLyrics").takeIf { it.isNotBlank() }
    )

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