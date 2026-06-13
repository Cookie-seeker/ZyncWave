package com.example.zyncwave2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object YtMusicApi {

    // ← Cambia esto cuando deploys en Railway
    private const val BASE_URL = "https://lyrica-6dy3.onrender.com"
    private const val TIMEOUT_MS = 20_000

    data class YtMusicResult(
        val title: String,
        val artistName: String,
        val plainLyrics: String?,
        val syncedLyrics: String?   // formato LRC estándar
    )

    suspend fun search(title: String, artist: String): Result<List<YtMusicResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                val encodedSong   = URLEncoder.encode(title,  "UTF-8")
                // sequence=4 → solo YouTube Music; timestamps=true para LRC
                val url = "$BASE_URL/lyrics/?artist=$encodedArtist&song=$encodedSong" +
                        "&timestamps=true&pass=true&sequence=4,2,3,1"

                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout    = TIMEOUT_MS
                    requestMethod  = "GET"
                }

                val code = conn.responseCode
                if (code != 200) return@runCatching emptyList()

                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)

                if (json.optString("status") != "success") return@runCatching emptyList()

                val data = json.optJSONObject("data") ?: return@runCatching emptyList()

                val plainLyrics  = data.optString("plain_lyrics").takeIf { it.isNotBlank() }
                val hasTimestamps = data.optBoolean("hasTimestamps", false)

                val syncedLyrics = if (hasTimestamps) {
                    val timedArray = data.optJSONArray("timed_lyrics")
                    timedArray?.let { convertToLrc(it) }
                } else null

                val resultTitle  = data.optString("title", title)
                val resultArtist = data.optString("artist", artist)

                listOf(
                    YtMusicResult(
                        title        = resultTitle,
                        artistName   = resultArtist,
                        plainLyrics  = plainLyrics,
                        syncedLyrics = syncedLyrics
                    )
                )
            }
        }

    /**
     * Convierte el array JSON de Lyrica a formato LRC estándar.
     * Input:  [{ "text": "...", "start_time": 5200, "end_time": 10400, "id": 1 }, ...]
     * Output: "[00:05.20] ...\n[00:10.40] ...\n"
     */
    private fun convertToLrc(timedArray: org.json.JSONArray): String {
        val sb = StringBuilder()
        for (i in 0 until timedArray.length()) {
            val item = timedArray.getJSONObject(i)
            val text      = item.optString("text", "")
            val startMs   = item.optLong("start_time", 0L)
            sb.append("[${formatLrcTime(startMs)}]$text\n")
        }
        return sb.toString().trimEnd()
    }

    /** Convierte milisegundos → [mm:ss.xx] */
    private fun formatLrcTime(ms: Long): String {
        val totalSec  = ms / 1000
        val centis    = (ms % 1000) / 10
        val minutes   = totalSec / 60
        val seconds   = totalSec % 60
        return "%02d:%02d.%02d".format(minutes, seconds, centis)
    }
}