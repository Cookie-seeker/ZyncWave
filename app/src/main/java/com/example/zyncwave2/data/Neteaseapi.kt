package com.example.zyncwave2.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente para la API no oficial de NetEase Music (music.163.com).
 * Usada ampliamente en apps open source (OuterTune, ViMusic, etc.).
 * No requiere autenticación para búsqueda y letras públicas.
 *
 * Endpoints usados:
 *  - /api/search/get → buscar canciones por query
 *  - /api/song/lyric → obtener LRC por song ID
 */
object NetEaseApi {

    private const val SEARCH_URL = "https://music.163.com"
    private const val LYRICS_URL = "https://netease-cloud-music-api-five-nu.vercel.app"
    private const val TIMEOUT  = 10000

    data class NetEaseResult(
        val id: Long,
        val title: String,
        val artistName: String,
        val albumName: String,
        val duration: Double,        // en milisegundos desde la API
        val plainLyrics: String?,
        val syncedLyrics: String?    // LRC con timestamps [mm:ss.xx]
    )

    /**
     * Busca canciones en NetEase por título + artista.
     * Retorna lista de resultados para que el usuario elija.
     */
    suspend fun search(
        title: String,
        artist: String
    ): Result<List<NetEaseResult>> = withContext(Dispatchers.IO) {
        try {
            val query   = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("$SEARCH_URL/api/search/get?s=$query&type=1&limit=10&offset=0")
            val conn    = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT
                readTimeout    = TIMEOUT
                setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/90.0")
                setRequestProperty("Referer", "https://music.163.com/")
                requestMethod = "GET"
            }

            val code = conn.responseCode
            if (code != 200) return@withContext Result.failure(Exception("HTTP $code"))

            val body   = conn.inputStream.bufferedReader().readText()
            val root   = JSONObject(body)
            val result = root.optJSONObject("result")
                ?: return@withContext Result.failure(Exception("Respuesta inválida"))

            val songs  = result.optJSONArray("songs")
                ?: return@withContext Result.success(emptyList())

            val results = mutableListOf<NetEaseResult>()
            for (i in 0 until songs.length()) {
                val song = songs.getJSONObject(i)

                // Artistas: puede haber varios, unirlos con ", "
                val artistsArray = song.optJSONArray("artists")
                val artistNames  = buildString {
                    if (artistsArray != null) {
                        for (j in 0 until artistsArray.length()) {
                            if (j > 0) append(", ")
                            append(artistsArray.getJSONObject(j).optString("name", ""))
                        }
                    }
                }

                val albumName = song.optJSONObject("album")
                    ?.optString("name", "") ?: ""

                results.add(
                    NetEaseResult(
                        id          = song.optLong("id", 0L),
                        title       = song.optString("name", ""),
                        artistName  = artistNames,
                        albumName   = albumName,
                        duration    = song.optDouble("duration", 0.0),
                        plainLyrics  = null,   // se carga con fetchLyrics
                        syncedLyrics = null
                    )
                )
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene las letras LRC de una canción por su ID.
     * Retorna un NetEaseResult actualizado con plainLyrics y/o syncedLyrics.
     *
     * El endpoint retorna:
     *  - lrc.lyric   → letras con timestamps [mm:ss.xx] (LRC estándar)
     *  - klyric.lyric → letras palabra por palabra (karaoke, ignoramos)
     *  - tlyric.lyric → traducción (ignoramos por ahora)
     */
    suspend fun fetchLyrics(songId: Long): Result<Pair<String?, String?>> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$LYRICS_URL/lyric?id=$songId")
                Log.d("LyricsDebug", "fetchLyrics → URL: $url")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT
                    readTimeout    = TIMEOUT
                    setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/90.0")
                    setRequestProperty("Referer", "https://music.163.com/")
                    requestMethod = "GET"
                }

                val code = conn.responseCode
                Log.d("LyricsDebug", "fetchLyrics → HTTP code: $code")
                if (code != 200) return@withContext Result.failure(Exception("HTTP $code"))

                val body = conn.inputStream.bufferedReader().readText()
                Log.d("LyricsDebug", "fetchLyrics → body snippet: ${body.take(300)}")
                val root = JSONObject(body)

                // LRC sincronizado
                val syncedRaw = root.optJSONObject("lrc")
                    ?.optString("lyric")
                    ?.takeIf { it.isNotBlank() }
                Log.d("LyricsDebug", "fetchLyrics → syncedRaw: ${syncedRaw?.take(100)}")

                // Verificar que realmente tiene timestamps, no es solo texto plano
                val syncedLyrics = if (syncedRaw != null && LrcParser.isLrc(syncedRaw)) {
                    syncedRaw
                } else null

                // Letras planas: si el LRC no tiene timestamps, usarlo como plano
                val plainLyrics = if (syncedLyrics == null && syncedRaw != null) {
                    // Limpiar líneas de metadatos [ar:], [ti:], etc. si las hubiera
                    syncedRaw
                        .lines()
                        .filter { !it.matches(Regex("\\[(ar|ti|al|by|offset|re|ve):[^]]*]")) }
                        .joinToString("\n")
                        .trim()
                        .takeIf { it.isNotBlank() }
                } else null

                Result.success(Pair(plainLyrics, syncedLyrics))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}