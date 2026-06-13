package com.example.zyncwave2.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * Todas las canciones ordenadas por título.
     * El filtro por carpetas se aplica en el Repository, no aquí,
     * porque Room no soporta LIKE dinámico por lista de carpetas.
     */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE data = :path")
    suspend fun getByPath(path: String): SongEntity?

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    /** Proyección mínima para el sync inteligente — no carga todos los campos */
    @Query("SELECT data, lastModified FROM songs")
    suspend fun getPathsAndModified(): List<PathModified>

    // ── Escritura ─────────────────────────────────────────────────────────────

    @Upsert
    suspend fun upsert(song: SongEntity)

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("""
        UPDATE songs SET
            title       = :title,
            artist      = :artist,
            album       = :album,
            genre       = :genre,
            trackNumber = :trackNumber,
            discNumber  = :discNumber
        WHERE id = :id
    """)
    suspend fun updateMetadata(
        id: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        trackNumber: Int,
        discNumber: Int
    )

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Elimina canciones cuyos paths ya no existen en disco.
     * Room no soporta NOT IN con lista dinámica grande — se hace por lotes en el repo.
     */
    @Query("DELETE FROM songs WHERE data = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM songs")
    suspend fun clearAll()

}

/** Proyección mínima para comparar caché vs disco sin cargar todo */
data class PathModified(
    val data: String,
    val lastModified: Long
)