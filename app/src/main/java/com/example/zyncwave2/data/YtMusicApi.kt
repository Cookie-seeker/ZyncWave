package com.example.zyncwave2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object YtMusicApi {

    // ← Cambia esto cuando deploys en Railway
    private const val BASE_URL = "https://zyncwave-lyrics.onrender.com"
    private const val TIMEOUT_MS = 20_000

    data class YtMusicResult(
        val title: String,
        val artistName: String,
        val plainLyrics: String?,
        val syncedLyrics: String?
    )

    suspend fun search(title: String, artist: String): Result<List<YtMusicResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                val encodedSong   = URLEncoder.encode(title,  "UTF-8")
                val url = "$BASE_URL/lyrics?title=$encodedSong&artist=$encodedArtist"

                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout    = TIMEOUT_MS
                    requestMethod  = "GET"
                }

                if (conn.responseCode != 200) return@runCatching emptyList()

                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)

                if (json.optString("status") != "success") return@runCatching emptyList()

                val lyrics = json.optString("lyrics").takeIf { it.isNotBlank() }
                    ?: return@runCatching emptyList()

                listOf(
                    YtMusicResult(
                        title        = json.optString("title", title),
                        artistName   = json.optString("artist", artist),
                        plainLyrics  = lyrics,
                        syncedLyrics = null
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