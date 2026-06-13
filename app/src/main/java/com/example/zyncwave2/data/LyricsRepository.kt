package com.example.zyncwave2.data

import android.content.Context

/**
 * Repositorio central de letras.
 * Coordina múltiples fuentes — el usuario elige cuál usar desde la UI.
 *
 * Fuentes disponibles:
 *  - LrcLib     → letras sincronizadas (LRC) y planas. Mejor cobertura general.
 *  - LyricsOvh  → letras planas. API pública sin key, sin registro.
 *  - NetEase    → letras sincronizadas (LRC). Excelente cobertura asiática y pop global.
 */
object LyricsRepository {

    // ── Fuentes disponibles ───────────────────────────────────────────────────

    enum class Source(val label: String) {
        LRCLIB("LrcLib"),
        LYRICSOVH("Lyrics.ovh"),
        NETEASE("NetEase"),
        GENIUS("Genius"),
        YTMUSIC("YT Music")
    }

    // ── Modelo unificado de resultado ─────────────────────────────────────────

    data class LyricsResult(
        val title: String,
        val artistName: String,
        val albumName: String,
        val duration: String,
        val plainLyrics: String?,
        val syncedLyrics: String?,
        val source: Source,
        internal val netEaseSongId: Long? = null,
        internal val geniusUrl: String? = null

    )

    // ── Búsqueda por fuente ───────────────────────────────────────────────────

    suspend fun search(
        title: String,
        artist: String,
        source: Source
    ): Result<List<LyricsResult>> {
        return when (source) {
            Source.LRCLIB    -> searchLrcLib(title, artist)
            Source.LYRICSOVH -> searchLyricsOvh(title, artist)
                Source.GENIUS   -> searchGenius(title, artist)
            Source.NETEASE   -> searchNetEase(title, artist)
            Source.YTMUSIC   -> searchYtMusic(title, artist)
        }
    }

    private suspend fun searchLrcLib(
        title: String,
        artist: String
    ): Result<List<LyricsResult>> {
        return LrcLibApi.search(title, artist).map { list ->
            list.map { r ->
                LyricsResult(
                    title        = r.title,
                    artistName   = r.artistName,
                    albumName    = r.albumName,
                    duration     = formatDuration(r.duration),
                    plainLyrics  = r.plainLyrics,
                    syncedLyrics = r.syncedLyrics,
                    source       = Source.LRCLIB
                )
            }
        }
    }

    private suspend fun searchLyricsOvh(
        title: String,
        artist: String
    ): Result<List<LyricsResult>> {
        return LyricsOvhApi.search(title, artist).map { list ->
            list.map { r ->
                LyricsResult(
                    title        = r.title,
                    artistName   = r.artistName,
                    albumName    = "",
                    duration     = "",
                    plainLyrics  = r.plainLyrics,
                    syncedLyrics = null,
                    source       = Source.LYRICSOVH
                )
            }
        }
    }

    private suspend fun searchGenius(
        title: String,
        artist: String
    ): Result<List<LyricsResult>> {
        return GeniusApi.search(title, artist).map { list ->
            list.map { r ->
                LyricsResult(
                    title        = r.title,
                    artistName   = r.artistName,
                    albumName    = r.albumName,
                    duration     = "",
                    plainLyrics  = null,  // lazy load
                    syncedLyrics = null,
                    source       = Source.GENIUS,
                    geniusUrl    = r.url  // ← necesitas este campo nuevo
                )
            }
        }
    }

    private suspend fun searchNetEase(
        title: String,
        artist: String
    ): Result<List<LyricsResult>> {
        return NetEaseApi.search(title, artist).map { list ->
            list.map { r ->
                LyricsResult(
                    title         = r.title,
                    artistName    = r.artistName,
                    albumName     = r.albumName,
                    duration      = formatDuration(r.duration / 1000.0),
                    plainLyrics   = null,
                    syncedLyrics  = null,
                    source        = Source.NETEASE,
                    netEaseSongId = r.id
                )
            }
        }
    }

    private suspend fun searchYtMusic(
        title: String,
        artist: String
    ): Result<List<LyricsResult>> {
        return YtMusicApi.search(title, artist).map { list ->
            list.map { r ->
                LyricsResult(
                    title        = r.title,
                    artistName   = r.artistName,
                    albumName    = "",
                    duration     = "",
                    plainLyrics  = r.plainLyrics,
                    syncedLyrics = r.syncedLyrics,
                    source       = Source.YTMUSIC
                )
            }
        }
    }

    /**
     * NetEase necesita fetch extra para obtener las letras.
     * LrcLib y LyricsOvh ya las traen en la búsqueda.
     */
    suspend fun fetchLyricsIfNeeded(result: LyricsResult): LyricsResult {
        return when (result.source) {
            Source.NETEASE -> {
                if (result.plainLyrics != null || result.syncedLyrics != null) return result
                val id = result.netEaseSongId ?: return result
                NetEaseApi.fetchLyrics(id).fold(
                    onSuccess = { (plain, synced) ->
                        result.copy(plainLyrics = plain, syncedLyrics = synced)
                    },
                    onFailure = { result }
                )
            }
            Source.LRCLIB, Source.LYRICSOVH, Source.YTMUSIC -> result

            Source.GENIUS -> {
                if (result.plainLyrics != null) return result
                val url = result.geniusUrl ?: return result
                GeniusApi.fetchLyrics(url).fold(
                    onSuccess = { lyrics -> result.copy(plainLyrics = lyrics) },
                    onFailure = { result }
                )
            }
        }
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    fun saveResult(
        context: Context,
        songId: Long,
        result: LyricsResult,
        preferSynced: Boolean = true
    ) {
        val lyrics = when {
            preferSynced && result.syncedLyrics != null -> result.syncedLyrics
            result.plainLyrics != null                  -> result.plainLyrics
            result.syncedLyrics != null                 -> result.syncedLyrics
            else -> return
        }
        LyricsManager.saveLyrics(context, songId, lyrics)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun loadLocal(context: Context, songId: Long): String =
        LyricsManager.loadLyrics(context, songId)

    fun formatDuration(seconds: Double): String {
        val total = seconds.toInt()
        return "%d:%02d".format(total / 60, total % 60)
    }

    // Compatibilidad con llamadas antiguas
    @Deprecated("Usa search(title, artist, Source.LRCLIB)",
        ReplaceWith("search(title, artist, LyricsRepository.Source.LRCLIB)"))
    suspend fun searchOnLrcLib(
        title: String,
        artist: String
    ): Result<List<LrcLibApi.LrcLibResult>> = LrcLibApi.search(title, artist)
}