package com.example.zyncwave2.data

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

object EqualizerManager {

    var equalizer: Equalizer? = null
    var bassBoost: BassBoost? = null
    var virtualizer: Virtualizer? = null
    var reverb: EnvironmentalReverb? = null

    var isEnabled = false

    fun init(audioSessionId: Int) {
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            virtualizer = Virtualizer(0, audioSessionId).apply { enabled = true }
            reverb = EnvironmentalReverb(0, audioSessionId).apply { enabled = true }
            isEnabled = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEqualizerBand(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }

    fun setBassBoost(strength: Short) {
        bassBoost?.setStrength(strength)
    }

    fun setVirtualizer(strength: Short) {
        virtualizer?.setStrength(strength)
    }

    fun setReverb(preset: Short) {
        reverb?.apply {
            when (preset.toInt()) {
                0 -> {
                    roomLevel = -9000
                    roomHFLevel = 0
                    decayTime = 100
                    reflectionsLevel = -9000
                    reverbLevel = -9000
                }
                1 -> {
                    roomLevel = -1000
                    roomHFLevel = -600
                    decayTime = 1490
                    reflectionsLevel = -389
                    reverbLevel = -1300
                }
                2 -> {
                    roomLevel = -1000
                    roomHFLevel = -600
                    decayTime = 2495
                    reflectionsLevel = -1269
                    reverbLevel = -400
                }
                3 -> {
                    roomLevel = -1000
                    roomHFLevel = -600
                    decayTime = 7239
                    reflectionsLevel = -2270
                    reverbLevel = -400
                }
                4 -> {
                    roomLevel = 0
                    roomHFLevel = 0
                    decayTime = 2910
                    reflectionsLevel = -2270
                    reverbLevel = -1800
                }
            }
        }
    }

    fun savePreferences(context: Context) {
        val prefs = context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Guardar bandas del EQ
        val eq = equalizer
        if (eq != null) {
            val numBands = eq.numberOfBands.toInt()
            editor.putInt("num_bands", numBands)
            for (i in 0 until numBands) {
                editor.putInt("band_$i", eq.getBandLevel(i.toShort()).toInt())
            }
        }

        // Guardar bass boost
        editor.putInt("bass_boost", bassBoost?.roundedStrength?.toInt() ?: 0)

        // Guardar virtualizer
        editor.putInt("virtualizer", virtualizer?.roundedStrength?.toInt() ?: 0)

        // Guardar reverb preset
        editor.putInt("reverb_preset", prefs.getInt("reverb_preset", 0))

        editor.apply()
        android.util.Log.d("EQ", "Preferencias guardadas")
    }

    fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)

        // Cargar bandas del EQ
        val numBands = prefs.getInt("num_bands", 0)
        for (i in 0 until numBands) {
            val level = prefs.getInt("band_$i", 0).toShort()
            equalizer?.setBandLevel(i.toShort(), level)
        }

        // Cargar bass boost
        val bassLevel = prefs.getInt("bass_boost", 0).toShort()
        bassBoost?.setStrength(bassLevel)

        // Cargar virtualizer
        val virtLevel = prefs.getInt("virtualizer", 0).toShort()
        virtualizer?.setStrength(virtLevel)

        // Cargar reverb
        val reverbPreset = prefs.getInt("reverb_preset", 0).toShort()
        setReverb(reverbPreset)
    }

    fun resetToDefault(context: Context) {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands.toInt()
        for (i in 0 until numBands) {
            eq.setBandLevel(i.toShort(), 0)
        }
        bassBoost?.setStrength(0)
        virtualizer?.setStrength(0)
        setReverb(0)

        // Limpiar preferencias guardadas
        context.getSharedPreferences("equalizer_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        reverb?.release()
        equalizer = null
        bassBoost = null
        virtualizer = null
        reverb = null
        isEnabled = false
    }
}