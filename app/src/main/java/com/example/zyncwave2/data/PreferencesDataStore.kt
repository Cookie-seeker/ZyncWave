package com.example.zyncwave2.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Extensión de Context que crea una instancia única del DataStore.
 * El delegate 'preferencesDataStore' garantiza que solo existe un DataStore
 * con este nombre en toda la app — no hay que inicializarlo en App.kt.
 */
val Context.downloadPreferencesStore: DataStore<Preferences>
        by preferencesDataStore(name = "download_preferences")

/**
 * Claves para cada campo de DownloadPreferences.
 * Centralizadas aquí para evitar typos al leer/escribir.
 */
object PreferencesKeys {
    val AUDIO_QUALITY    = stringPreferencesKey("audio_quality")
    val RATE_LIMIT       = stringPreferencesKey("rate_limit")
    val EMBED_SUBTITLES  = booleanPreferencesKey("embed_subtitles")
    val SUBTITLE_LANG    = stringPreferencesKey("subtitle_lang")
}