package com.example.zyncwave2.data

import android.content.Context
import android.os.ParcelFileDescriptor
import com.example.zyncwave2.data.db.AppDatabase
import com.example.zyncwave2.data.db.SongEntity
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

class SongRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).songDao()

    // ─────────────────────────────────────────────────────────────────────────
    // Extensiones por motor — mismo criterio que MetadataRepository
    // ─────────────────────────────────────────────────────────────────────────
    private val TAGLIB_EXT = setOf("opus")

    private val JAUDIOTAGGER_EXT = setOf(
        "mp3", "flac", "m4a", "ogg", "wav", "wma", "aac", "dsf", "aiff"
    )

    private val AUDIO_EXT = TAGLIB_EXT + JAUDIOTAGGER_EXT

    // ─────────────────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────────────────

    fun getSongsFlow(folders: Set<String>): Flow<List<Songs>> {
        return dao.getAllFlow().map { entities ->
            entities
                .filter { entity ->
                    folders.isEmpty() || folders.any { entity.data.startsWith(it) }
                }
                .map { it.toSongs() }
        }
    }

    suspend fun getById(id: Long): SongEntity? = dao.getById(id)

    /**
     * Sync inteligente: solo re-escanea archivos nuevos o modificados.
     * Elimina de Room los archivos que ya no existen en disco.
     */
    suspend fun syncWithDisk(folders: Set<String>) = withContext(Dispatchers.IO) {
        if (folders.isEmpty()) return@withContext
        android.util.Log.d("SongRepo", "=== Sync inteligente iniciado ===")

        val audioFiles = folders.flatMap { findAudioFiles(File(it)) }
        android.util.Log.d("SongRepo", "Archivos en disco: ${audioFiles.size}")

        val cachedMap = dao.getPathsAndModified().associate { it.data to it.lastModified }

        val toScan = audioFiles.filter { file ->
            val cachedModified = cachedMap[file.absolutePath]
            cachedModified == null || cachedModified != file.lastModified()
        }
        android.util.Log.d("SongRepo", "A escanear: ${toScan.size} (opus=${toScan.count { it.extension.lowercase() == "opus" }})")

        val scanned = toScan.mapNotNull { readMetadata(it) }
        if (scanned.isNotEmpty()) dao.upsertAll(scanned)

        val activePaths = audioFiles.map { it.absolutePath }.toSet()
        val orphans     = cachedMap.keys.filter { it !in activePaths }
        orphans.forEach { dao.deleteByPath(it) }
        if (orphans.isNotEmpty()) {
            android.util.Log.d("SongRepo", "Huérfanos eliminados: ${orphans.size}")
        }

        android.util.Log.d("SongRepo", "=== Sync completado ===")
    }

    /**
     * Escaneo completo: borra todo Room y re-escanea desde cero.
     */
    suspend fun fullScan(folders: Set<String>) = withContext(Dispatchers.IO) {
        if (folders.isEmpty()) return@withContext
        android.util.Log.d("SongRepo", "=== Escaneo completo iniciado ===")

        val audioFiles = folders.flatMap { findAudioFiles(File(it)) }
        android.util.Log.d("SongRepo", "Archivos encontrados: ${audioFiles.size} (opus=${audioFiles.count { it.extension.lowercase() == "opus" }})")

        val entities = audioFiles.mapNotNull { readMetadata(it) }
        dao.clearAll()
        if (entities.isNotEmpty()) dao.upsertAll(entities)

        android.util.Log.d("SongRepo", "Escaneadas: ${entities.size} canciones")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Escaneo de archivos
    // ─────────────────────────────────────────────────────────────────────────

    private fun findAudioFiles(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXT }
            .toList()
    }

    /**
     * Dispatch por extensión — mismo patrón que MetadataRepository.
     * .opus  → TagLib (Kyant0)
     * resto  → JAudioTagger (fork Adonai)
     */
    private fun readMetadata(file: File): SongEntity? {
        val ext = file.extension.lowercase()
        return when {
            ext in TAGLIB_EXT       -> readMetadataWithTagLib(file)
            ext in JAUDIOTAGGER_EXT -> readMetadataWithJAudioTagger(file)
            else                    -> readMetadataWithJAudioTagger(file) // fallback
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Motor 1: JAudioTagger  (mp3, flac, ogg-vorbis, m4a, wav, wma…)
    // ─────────────────────────────────────────────────────────────────────────

    private fun readMetadataWithJAudioTagger(file: File): SongEntity? {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag       = audioFile.tag
            val props     = audioFile.audioHeader

            val albumName = tag?.getFirst(FieldKey.ALBUM).orEmpty()
            val albumId   = albumName.hashCode().toLong().and(0xFFFFFFFFL)
            val id        = file.absolutePath.hashCode().toLong().and(0xFFFFFFFFL)

            SongEntity(
                id           = id,
                title        = tag?.getFirst(FieldKey.TITLE).orEmpty()
                    .ifBlank { file.nameWithoutExtension },
                artist       = tag?.getFirst(FieldKey.ARTIST).orEmpty(),
                album        = albumName,
                genre        = tag?.getFirst(FieldKey.GENRE).orEmpty(),
                trackNumber  = tag?.getFirst(FieldKey.TRACK)
                    ?.filter { it.isDigit() }
                    ?.toIntOrNull() ?: 0,
                discNumber   = tag?.getFirst(FieldKey.DISC_NO)
                    ?.filter { it.isDigit() }
                    ?.toIntOrNull() ?: 1,
                data         = file.absolutePath,
                albumId      = albumId,
                duration     = (props?.trackLength?.toLong() ?: 0L) * 1000L,
                lastModified = file.lastModified()
            )
        } catch (e: Exception) {
            android.util.Log.w("SongRepo", "[JAT] No se pudo leer ${file.name}: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Motor 2: TagLib via Kyant0  (.opus)
    //
    // TagLib trabaja con ParcelFileDescriptor. Para archivos locales
    // se abre con ParcelFileDescriptor.open(file, MODE_READ_ONLY).
    //
    // Vorbis Comments en Opus usan claves en mayúsculas:
    //   TITLE, ARTIST, ALBUM, GENRE, TRACKNUMBER, DISCNUMBER, DATE
    // ─────────────────────────────────────────────────────────────────────────

    private fun readMetadataWithTagLib(file: File): SongEntity? {
        return try {
            val pfd = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            val metadata = pfd.use {
                TagLib.getMetadata(it.dup().detachFd(), readPictures = false)
            }

            if (metadata == null) {
                android.util.Log.w("SongRepo", "[TagLib] metadata null para ${file.name}")
                return null
            }

            val tags = metadata.propertyMap

            fun tag(key: String) = tags[key]?.firstOrNull().orEmpty()

            val title     = tag("TITLE").ifBlank { file.nameWithoutExtension }
            val artist    = tag("ARTIST")
            val albumName = tag("ALBUM")
            val genre     = tag("GENRE")

            val trackNumber = tag("TRACKNUMBER")
                .filter { it.isDigit() }
                .toIntOrNull() ?: 0

            val discNumber = tag("DISCNUMBER")
                .filter { it.isDigit() }
                .toIntOrNull() ?: 1

            // Duración en segundos desde audioProperties
            val durationMs = try {
                val pfd2 = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val props = pfd2.use { TagLib.getAudioProperties(it.dup().detachFd()) }
                (props?.length?.toLong() ?: 0L) * 1000L
            } catch (e: Exception) {
                0L
            }

            val albumId = albumName.hashCode().toLong().and(0xFFFFFFFFL)
            val id      = file.absolutePath.hashCode().toLong().and(0xFFFFFFFFL)

            android.util.Log.d(
                "SongRepo",
                "[TagLib] Leído: title='$title' artist='$artist' album='$albumName' duration=${durationMs}ms"
            )

            SongEntity(
                id           = id,
                title        = title,
                artist       = artist,
                album        = albumName,
                genre        = genre,
                trackNumber  = trackNumber,
                discNumber   = discNumber,
                data         = file.absolutePath,
                albumId      = albumId,
                duration     = durationMs,
                lastModified = file.lastModified()
            )

        } catch (e: Exception) {
            android.util.Log.w("SongRepo", "[TagLib] No se pudo leer ${file.name}: ${e.message}")
            null
        }
    }
}