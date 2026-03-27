package com.example.zyncwave2.data

import android.content.Context
import android.provider.MediaStore

fun getSongs(context: Context, folders: Set<String> = emptySet()): List<Songs> {
    // Si no hay carpetas seleccionadas, no escanear nada
    if (folders.isEmpty()) return emptyList()

    val songs = mutableListOf<Songs>()
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val selection = "${MediaStore.Audio.Media.IS_MUSIC}!=0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ALBUM
    )

    val cursor = context.contentResolver.query(
        uri, projection, selection, null, sortOrder
    )

    cursor?.use {
        val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val albumNameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

        while (it.moveToNext()) {
            val data = it.getString(dataCol)
            if (folders.any { folder -> data.startsWith(folder) }) {
                songs.add(
                    Songs(
                        id = it.getLong(idCol),
                        title = it.getString(titleCol),
                        artists = it.getString(artistCol),
                        data = data,
                        albumId = it.getLong(albumIdCol),
                        albumName = it.getString(albumNameCol)
                    )
                )
            }
        }
    }

    return songs
}