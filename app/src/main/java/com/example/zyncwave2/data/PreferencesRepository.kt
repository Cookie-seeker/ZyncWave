package com.example.zyncwave2.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repositorio que expone las preferencias de descarga como un Flow.
 * El ViewModel lo observa con collectAsState() igual que cualquier StateFlow.
 *
 * Uso:
 *   val repo = PreferencesRepository(context)
 *   repo.preferencesFlow.collect { prefs -> ... }
 *   repo.saveAudioQuality(AudioQuality.HIGH)
 */
class PreferencesRepository(private val context: Context) {

    /**
     * Flow que emite un DownloadPreferences cada vez que cambia algún valor
     * en el DataStore. Emite inmediatamente con los valores actuales al suscribirse.
     */
    val preferencesFlow: Flow<YtDlpManager.DownloadPreferences> =
        context.downloadPreferencesStore.data.map { prefs ->
            YtDlpManager.DownloadPreferences(
                audioQuality = YtDlpManager.AudioQuality.entries.find {
                    it.value == prefs[PreferencesKeys.AUDIO_QUALITY]
                } ?: YtDlpManager.AudioQuality.BEST,
                rateLimit = prefs[PreferencesKeys.RATE_LIMIT]
                    ?.takeIf { it.isNotBlank() },
                embedSubtitles = prefs[PreferencesKeys.EMBED_SUBTITLES] ?: false,
                subtitleLang = prefs[PreferencesKeys.SUBTITLE_LANG] ?: "es,en"
            )
        }

    suspend fun saveAudioQuality(quality: YtDlpManager.AudioQuality) {
        context.downloadPreferencesStore.edit { prefs ->
            prefs[PreferencesKeys.AUDIO_QUALITY] = quality.value
        }
    }

    suspend fun saveRateLimit(limit: String?) {
        context.downloadPreferencesStore.edit { prefs ->
            prefs[PreferencesKeys.RATE_LIMIT] = limit?.trim()?.uppercase() ?: ""
        }
    }

    suspend fun saveEmbedSubtitles(value: Boolean) {
        context.downloadPreferencesStore.edit { prefs ->
            prefs[PreferencesKeys.EMBED_SUBTITLES] = value
        }
    }

    suspend fun saveSubtitleLang(lang: String) {
        context.downloadPreferencesStore.edit { prefs ->
            prefs[PreferencesKeys.SUBTITLE_LANG] = lang.trim()
        }
    }
}