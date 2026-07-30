package com.example.zyncwave2.data

data class DownloadState(
    //Campo URL
    val urlInput: String = "",

    //Estado del binario
    val isBinaryReady: Boolean = false,
    val ytDlpVersion: String = "",

    //Carga de formatos
    val isLoadingFormats: Boolean = false,
    val availableFormats: List<VideoFormat> = emptyList(),
    val selectedFormat: VideoFormat? = null,
    val videoTitle: String = "",
    val showFormats: Boolean = false,

    //Descarga activa
    val isDownloading: Boolean = false,
    val statusMessage: String = "",
    val currentProgress: Float = 0f,

    //Cola de descargas pendientes
    val queue: List<DownloadItem> = emptyList(),

    //Preferencias de descarga
    // Persisten entre descargas — el usuario las configura una vez.
    val preferences: YtDlpManager.DownloadPreferences = YtDlpManager.DownloadPreferences(),
    val showPreferences: Boolean = false,

    // Actualizador
    val isUpdating: Boolean = false,
    val updateMessage: String = "",
    val useNightly: Boolean = false,
    val showUpdatePanel: Boolean = false,

    //Historial (completados + errores)
    val downloadList: List<DownloadItem> = emptyList(),

    //Pantalla de ajustes
    val showSettings: Boolean = false,
)