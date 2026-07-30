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
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.GENRE,
        MediaStore.Audio.Media.TRACK,
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
        val genreCol = it.getColumnIndex(MediaStore.Audio.Media.GENRE)
        val trackCol = it.getColumnIndex(MediaStore.Audio.Media.TRACK)

        while (it.moveToNext()) {
            val data = it.getString(dataCol)
            if (folders.any { folder -> data.startsWith(folder) }) {

                val genreVal = if (genreCol >= 0) it.getString(genreCol) else "columna no existe"
                val trackVal = if (trackCol >= 0) it.getInt(trackCol) else -1
                android.util.Log.d("getSongs", "archivo=$data | genre=$genreVal | track=$trackVal")

                songs.add(
                    Songs(
                        id        = it.getLong(idCol),
                        title     = it.getString(titleCol),
                        artists   = it.getString(artistCol),
                        data      = data,
                        albumId   = it.getLong(albumIdCol),
                        albumName = it.getString(albumNameCol),
                        genre     = if (genreCol >= 0) it.getString(genreCol) else null,
                        trackNumber = if (trackCol >= 0) {
                            val track = it.getInt(trackCol)
                            if (track > 1000) track % 1000 else track
                        } else null,
                        discNumber = if (trackCol >= 0) {
                            val track = it.getInt(trackCol)
                            if (track > 1000) track / 1000 else 1
                        } else null
                    )
                )
            }
        }
    }
    return songs
}