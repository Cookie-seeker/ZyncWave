package com.example.zyncwave2.data

data class DownloadItem(
    val id: String,
    val title: String,
    val url: String,
    val type: String,
    val status: String,
    val progress: Float
)
