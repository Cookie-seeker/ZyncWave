package com.example.zyncwave2.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Songs(
    val id: Long,
    val title: String?,
    val artists: String?,
    val data: String,
    val albumId: Long,
    val albumName: String?
): Parcelable