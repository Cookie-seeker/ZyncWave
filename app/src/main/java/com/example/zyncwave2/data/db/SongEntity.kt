package com.example.zyncwave2.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.zyncwave2.data.Songs

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val trackNumber: Int,       // número de la pista dentro del disco
    val discNumber: Int,        // número de disco
    val data: String,           // path absoluto del archivo
    val albumId: Long,
    val duration: Long,         // ms
    val lastModified: Long      // timestamp del archivo en disco — detecta cambios
) {
    // Convierte a Songs (data class existente) para no romper el código actual
    fun toSongs(): Songs = Songs(
        id        = id,
        title     = title.ifBlank { null },
        artists   = artist.ifBlank { null },
        data      = data,
        albumId   = albumId,
        albumName = album.ifBlank { null },
        genre     = genre.ifBlank { null },
        trackNumber = trackNumber.takeIf { it > 0 },
        discNumber  = discNumber.takeIf { it > 1 }
    )

    companion object {
        // Convierte desde Songs + campos extra opcionales
        fun fromSongs(
            song: Songs,
            genre: String       = "",
            trackNumber: Int    = 0,
            discNumber: Int     = 1,
            duration: Long      = 0L,
            lastModified: Long  = 0L
        ): SongEntity = SongEntity(
            id           = song.id,
            title        = song.title.orEmpty(),
            artist       = song.artists.orEmpty(),
            album        = song.albumName.orEmpty(),
            genre        = genre,
            trackNumber  = trackNumber,
            discNumber   = discNumber,
            data         = song.data,
            albumId      = song.albumId,
            duration     = duration,
            lastModified = lastModified
        )
    }
}