package com.example.zyncwave2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente para la API oficial de Genius.
 * Usa Client Access Token propio — búsqueda via API REST,
 * letras via scraping del HTML de la página de la canción.
 */
object GeniusApi {

    private const val BASE_URL    = "https://api.genius.com"
    private const val TOKEN       = // Create your own API at: https://genius.com/api-clients/new
    private const val TIMEOUT     = 15000
    private const val MAX_RETRY   = 3
    private const val RETRY_DELAY = 1500L

    data class GeniusResult(
        val id: Int,
        val title: String,
        val artistName: String,
        val albumName: String,
        val url: String,
        val plainLyrics: String?,
        val syncedLyrics: String?
    )

    /**
     * Busca canciones en Genius por título + artista.
     * Reintenta hasta MAX_RETRY veces ante fallos de red.
     */
    suspend fun search(
        title: String,
        artist: String
    ): Result<List<GeniusResult>> = withContext(Dispatchers.IO) {
        retry(MAX_RETRY) {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url   = URL("$BASE_URL/search?q=$query")
            val conn  = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT
                readTimeout    = TIMEOUT
                setRequestProperty("Authorization", "Bearer $TOKEN")
                setRequestProperty("User-Agent", "ZyncWave2/1.0")
                requestMethod = "GET"
            }

            val code = conn.responseCode
            if (code != 200) throw Exception("HTTP $code")

            val body = conn.inputStream.bufferedReader().readText()
            val root = JSONObject(body)
            val hits = root
                .getJSONObject("response")
                .getJSONArray("hits")

            val results = mutableListOf<GeniusResult>()
            for (i in 0 until hits.length()) {
                val hit = hits.getJSONObject(i)
                if (hit.optString("type") != "song") continue
                val result = hit.getJSONObject("result")
                results.add(
                    GeniusResult(
                        id           = result.optInt("id", 0),
                        title        = result.optString("title", ""),
                        artistName   = result.optJSONObject("primary_artist")
                            ?.optString("name", "") ?: "",
                        albumName    = "",
                        url          = result.optString("url", ""),
                        plainLyrics  = null,
                        syncedLyrics = null
                    )
                )
            }
            results
        }
    }

    /**
     * Descarga y extrae las letras planas de una página de Genius.
     * Scraping del HTML — busca contenedores data-lyrics-container.
     * Reintenta hasta MAX_RETRY veces.
     */
    suspend fun fetchLyrics(url: String): Result<String> = withContext(Dispatchers.IO) {
        retry(MAX_RETRY) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT
                readTimeout    = TIMEOUT
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 " +
                            "KHTML, like Gecko Chrome/112.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                requestMethod = "GET"
            }

            val code = conn.responseCode
            if (code != 200) throw Exception("HTTP $code")

            val html   = conn.inputStream.bufferedReader().readText()
            val lyrics = extractLyricsFromHtml(html)

            if (lyrics.isBlank()) throw Exception("No se encontraron letras")
            lyrics
        }
    }

    /**
     * Extrae el texto de los contenedores data-lyrics-container del HTML de Genius.
     * Limpia tags HTML y convierte <br> en saltos de línea.
     */
    private fun extractLyricsFromHtml(html: String): String {
        val sb = StringBuilder()

        val pattern = Regex(
            """data-lyrics-container="true"[^>]*>(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        for (match in pattern.findAll(html)) {
            var chunk = match.groupValues[1]

            chunk = chunk.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            chunk = chunk.replace(Regex("<[^>]+>"), "")
            chunk = chunk
                .replace("&amp;",  "&")
                .replace("&lt;",   "<")
                .replace("&gt;",   ">")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&apos;", "'")
                .replace("&#x60;", "`")

            sb.append(chunk.trim()).append("\n\n")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Ejecuta [block] hasta [times] veces con delay entre reintentos.
     * Retorna Result.success en el primer éxito, Result.failure si se agotan los intentos.
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
