package com.example.zyncwave2.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.zyncwave2.data.db.AppDatabase
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileOutputStream

// ─────────────────────────────────────────────────────────────────────────────
// Resultado sellado que devuelve el repositorio a la UI
// ─────────────────────────────────────────────────────────────────────────────
sealed class WriteResult {
    object Success : WriteResult()
    data class Error(val message: String, val cause: Throwable? = null) : WriteResult()
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatos soportados por cada motor
// ─────────────────────────────────────────────────────────────────────────────
private val JAUDIOTAGGER_FORMATS = setOf(
    "mp3", "flac", "ogg", "wav", "wave",
    "aif", "aiff", "mp4", "m4a", "m4p",
    "m4b", "wma", "dsf", "dff"
)

// Opus usa contenedor Ogg pero codec Opus — JAudioTagger no lo soporta.
// TagLib (Kyant0) lo maneja nativamente vía Vorbis Comments.
private val TAGLIB_FORMATS = setOf("opus")

private const val TAG = "MetadataRepository"

// ─────────────────────────────────────────────────────────────────────────────
class MetadataRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).songDao()

    // ─────────────────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Guarda título, artista, álbum, género, nº pista, nº disco y carátula opcional.
     *
     * Estrategia híbrida:
     *   • .opus  → TagLib  (Kyant0/taglib vía JNI — soporta Vorbis Comments en Ogg Opus)
     *   • resto  → JAudioTagger  (fork Adonai — mp3, flac, ogg-vorbis, m4a, wav, etc.)
     *
     * Flujo para ambos motores:
     *   1. Copiar original → temp en cacheDir
     *   2. Modificar tags en el temp
     *   3. Escribir temp de vuelta al archivo original (ContentResolver o File.copyTo)
     *   4. Actualizar Room
     *   5. Notificar MediaStore
     */
    suspend fun saveTags(
        songId: Long,
        filePath: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        trackNumber: Int,
        discNumber: Int,
        artworkUri: Uri? = null
    ): WriteResult = withContext(Dispatchers.IO) {
        val ext = File(filePath).extension.lowercase()
        Log.d(TAG, "[saveTags] songId=$songId ext=$ext path=$filePath")

        val result = when {
            ext in TAGLIB_FORMATS       -> saveTagsWithTagLib(
                songId, filePath, ext,
                title, artist, album, genre,
                trackNumber, discNumber, artworkUri
            )
            ext in JAUDIOTAGGER_FORMATS -> saveTagsWithJAudioTagger(
                songId, filePath, ext,
                title, artist, album, genre,
                trackNumber, discNumber, artworkUri
            )
            else -> {
                // Formato desconocido: intenta JAudioTagger como fallback genérico
                Log.w(TAG, "[saveTags] Formato desconocido '$ext' — intentando JAudioTagger")
                saveTagsWithJAudioTagger(
                    songId, filePath, ext,
                    title, artist, album, genre,
                    trackNumber, discNumber, artworkUri
                )
            }
        }

        if (result is WriteResult.Success) {
            // Room primero → la UI reacciona via Flow de inmediato
            dao.updateMetadata(
                id          = songId,
                title       = title,
                artist      = artist,
                album       = album,
                genre       = genre,
                trackNumber = trackNumber,
                discNumber  = discNumber
            )
            Log.d(TAG, "[saveTags] Room actualizado para songId=$songId")

            // MediaStore en background — no bloquea la UI
            notifyMediaStore(filePath, title, artist, album, genre, trackNumber, discNumber)
        }

        result
    }



    /**
     * Guarda solo la carátula sin tocar los tags de texto.
     * Misma lógica de dispatch por extensión.
     */
    suspend fun saveArtwork(
        songId: Long,
        filePath: String,
        artworkUri: Uri
    ): WriteResult = withContext(Dispatchers.IO) {
        val ext = File(filePath).extension.lowercase()
        Log.d(TAG, "[saveArtwork] songId=$songId ext=$ext")

        when {
            ext in TAGLIB_FORMATS       -> saveArtworkWithTagLib(songId, filePath, ext, artworkUri)
            ext in JAUDIOTAGGER_FORMATS -> saveArtworkWithJAudioTagger(songId, filePath, ext, artworkUri)
            else -> saveArtworkWithJAudioTagger(songId, filePath, ext, artworkUri)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Motor 1: JAudioTagger  (mp3, flac, ogg-vorbis, m4a, wav, wma, dsf…)
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveTagsWithJAudioTagger(
        songId: Long,
        filePath: String,
        ext: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        trackNumber: Int,
        discNumber: Int,
        artworkUri: Uri?
    ): WriteResult {
        var tempFile: File? = null
        var artTemp: File?  = null
        return try {
            val original = File(filePath)
            val workDir  = File(context.filesDir, "jat_work").also { it.mkdirs() }
            tempFile     = File(workDir, "jat_edit_${songId}_${System.currentTimeMillis()}.$ext")
            original.copyTo(tempFile, overwrite = true)
            tempFile.setWritable(true)
            System.setProperty("java.io.tmpdir", workDir.absolutePath)

            TagOptionSingleton.getInstance().isAndroid = true



            Log.d(TAG, "[JAT] tempFile=${tempFile.length()} bytes")

            val audioFile = AudioFileIO.read(tempFile)
            val tag       = audioFile.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE,  title)
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM,  album)
            if (genre.isNotBlank())   tag.setField(FieldKey.GENRE,   genre)
            else                      tag.deleteField(FieldKey.GENRE)
            if (trackNumber > 0)      tag.setField(FieldKey.TRACK,   trackNumber.toString())
            if (discNumber  > 1)      tag.setField(FieldKey.DISC_NO, discNumber.toString())

            if (artworkUri != null) {
                artTemp = File(context.cacheDir, "jat_art_${songId}.jpg")
                context.contentResolver.openInputStream(artworkUri)?.use { stream ->
                    FileOutputStream(artTemp!!).use { out -> stream.copyTo(out) }
                }
                val artwork = ArtworkFactory.createArtworkFromFile(artTemp!!)
                tag.deleteArtworkField()
                tag.setField(artwork)
            }

            audioFile.commit()
            artTemp?.delete(); artTemp = null

            // Verificación de escritura en modo debug
            val verify = AudioFileIO.read(tempFile)
            Log.d(TAG, "[JAT] verify → title='${verify.tag?.getFirst(FieldKey.TITLE)}'")

            writeBack(tempFile, original)
            tempFile.delete(); tempFile = null

            WriteResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "[JAT] error: ${e.message}", e)
            artTemp?.delete()
            tempFile?.delete()
            WriteResult.Error("Error JAudioTagger (.$ext): ${e.message}", e)
        }
    }

    private fun saveArtworkWithJAudioTagger(
        songId: Long,
        filePath: String,
        ext: String,
        artworkUri: Uri
    ): WriteResult {
        var tempFile: File? = null
        var artTemp: File?  = null
        return try {
            val original = File(filePath)
            val workDir  = File(context.filesDir, "jat_work").also { it.mkdirs() }
            tempFile     = File(workDir, "jat_art_edit_${songId}.$ext")
            original.copyTo(tempFile, overwrite = true)
            tempFile.setWritable(true)
            System.setProperty("java.io.tmpdir", workDir.absolutePath)


            TagOptionSingleton.getInstance().isAndroid = true


            val audioFile = AudioFileIO.read(tempFile)
            val tag       = audioFile.tagOrCreateAndSetDefault

            artTemp = File(context.cacheDir, "jat_art_img_${songId}.jpg")
            context.contentResolver.openInputStream(artworkUri)?.use { stream ->
                FileOutputStream(artTemp!!).use { out -> stream.copyTo(out) }
            }
            val artwork = ArtworkFactory.createArtworkFromFile(artTemp!!)
            tag.deleteArtworkField()
            tag.setField(artwork)
            audioFile.commit()

            artTemp.delete(); artTemp = null
            writeBack(tempFile, original)
            tempFile.delete(); tempFile = null

            android.media.MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            WriteResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "[JAT][artwork] error: ${e.message}", e)
            artTemp?.delete()
            tempFile?.delete()
            WriteResult.Error("Error JAudioTagger artwork (.$ext): ${e.message}", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Motor 2: TagLib via Kyant0  (.opus y cualquier formato soportado por TagLib)
    //
    // API de Kyant0/taglib:
    //   TagLib.getMetadata(fd: Int, readPictures: Boolean): Metadata?
    //   TagLib.savePropertyMap(fd: Int, propertyMap: Map<String, Array<String>>): Boolean
    //   TagLib.getPictures(fd: Int): Array<Picture>
    //   TagLib.savePictures(fd: Int, pictures: Array<Picture>): Boolean
    //
    // IMPORTANTE: Cada llamada a TagLib consume el fd. Hay que hacer
    //   pfd.dup().detachFd() para cada llamada independiente.
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveTagsWithTagLib(
        songId: Long,
        filePath: String,
        ext: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        trackNumber: Int,
        discNumber: Int,
        artworkUri: Uri?
    ): WriteResult {
        var tempFile: File? = null
        return try {
            val original = File(filePath)
            tempFile = File(context.cacheDir, "tl_edit_${songId}_${System.currentTimeMillis()}.$ext")
            original.copyTo(tempFile, overwrite = true)
            Log.d(TAG, "[TagLib] tempFile=${tempFile.length()} bytes ext=$ext")

            // Abrir con fd de lectura/escritura
            val pfd = ParcelFileDescriptor.open(
                tempFile,
                ParcelFileDescriptor.MODE_READ_WRITE
            )

            // Leer tags existentes para no perder campos que no estamos editando
            val existingMeta = pfd.use { TagLib.getMetadata(it.dup().detachFd()) }
            val propertyMap  = HashMap<String, Array<String>>().apply {
                existingMeta?.propertyMap?.let { putAll(it) }
                this["TITLE"]       = arrayOf(title)
                this["ARTIST"]      = arrayOf(artist)
                this["ALBUM"]       = arrayOf(album)
                if (genre.isNotBlank())  this["GENRE"]       = arrayOf(genre)
                else                     remove("GENRE")
                if (trackNumber > 0) this["TRACKNUMBER"]  = arrayOf(trackNumber.toString())
                if (discNumber  > 1) this["DISCNUMBER"]   = arrayOf(discNumber.toString())
            }

            // Escribir propertyMap en el temp file
            val pfd2 = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
            val saved = pfd2.use { TagLib.savePropertyMap(it.dup().detachFd(), propertyMap) }

            if (!saved) {
                Log.e(TAG, "[TagLib] savePropertyMap devolvió false para ext=$ext")
                tempFile.delete(); tempFile = null
                return WriteResult.Error("TagLib no pudo escribir los tags del archivo .$ext")
            }
            Log.d(TAG, "[TagLib] savePropertyMap OK")

            // Artwork
            if (artworkUri != null) {
                val mimeType  = context.contentResolver.getType(artworkUri) ?: "image/jpeg"
                val artBytes  = context.contentResolver.openInputStream(artworkUri)
                    ?.use { it.readBytes() }

                if (artBytes != null && artBytes.isNotEmpty()) {
                    val picture = Picture(
                        data        = artBytes,
                        description = "Front Cover",
                        pictureType = "Front Cover",
                        mimeType    = mimeType
                    )
                    val pfd3 = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
                    pfd3.use { TagLib.savePictures(it.dup().detachFd(), arrayOf(picture)) }
                    Log.d(TAG, "[TagLib] artwork guardado (${artBytes.size} bytes)")
                }
            }

            // Verificación rápida en debug
            val pfd4    = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val verify  = pfd4.use { TagLib.getMetadata(it.dup().detachFd()) }
            Log.d(TAG, "[TagLib] verify → title='${verify?.propertyMap?.get("TITLE")?.firstOrNull()}'")

            writeBack(tempFile, original)
            tempFile.delete(); tempFile = null

            WriteResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "[TagLib] error: ${e.message}", e)
            tempFile?.delete()
            WriteResult.Error("Error TagLib (.$ext): ${e.message}", e)
        }
    }

    private fun saveArtworkWithTagLib(
        songId: Long,
        filePath: String,
        ext: String,
        artworkUri: Uri
    ): WriteResult {
        var tempFile: File? = null
        return try {
            val original = File(filePath)
            tempFile = File(context.cacheDir, "tl_art_${songId}.$ext")
            original.copyTo(tempFile, overwrite = true)

            val mimeType = context.contentResolver.getType(artworkUri) ?: "image/jpeg"
            val artBytes = context.contentResolver.openInputStream(artworkUri)
                ?.use { it.readBytes() }

            if (artBytes == null || artBytes.isEmpty()) {
                tempFile.delete(); tempFile = null
                return WriteResult.Error("No se pudo leer la imagen seleccionada")
            }

            val picture = Picture(
                data        = artBytes,
                description = "Front Cover",
                pictureType = "Front Cover",
                mimeType    = mimeType
            )

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
            val saved = pfd.use { TagLib.savePictures(it.dup().detachFd(), arrayOf(picture)) }

            if (!saved) {
                tempFile.delete(); tempFile = null
                return WriteResult.Error("TagLib no pudo guardar la carátula en .$ext")
            }

            writeBack(tempFile, original)
            tempFile.delete(); tempFile = null

            android.media.MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            WriteResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "[TagLib][artwork] error: ${e.message}", e)
            tempFile?.delete()
            WriteResult.Error("Error TagLib artwork (.$ext): ${e.message}", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers compartidos
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Escribe el archivo temporal editado de vuelta a su ubicación original.
     * Intenta primero via ContentResolver (Android 10+); si no encuentra el URI
     * en MediaStore, hace una copia directa de archivo.
     */
    private fun writeBack(editedFile: File, originalFile: File) {
        val mediaUri = findMediaStoreUri(originalFile.absolutePath)
        if (mediaUri != null) {
            // "wt" = write + truncate: sobreescribe el contenido completo
            context.contentResolver.openOutputStream(mediaUri, "wt")?.use { out ->
                editedFile.inputStream().use { it.copyTo(out) }
            }
            Log.d(TAG, "[writeBack] ContentResolver OK → $mediaUri")
        } else {
            editedFile.copyTo(originalFile, overwrite = true)
            Log.d(TAG, "[writeBack] File.copyTo OK → ${originalFile.absolutePath}")
        }
    }

    /**
     * Actualiza los campos en MediaStore y luego lanza MediaScanner
     * para que el sistema reindexe el archivo.
     */
    private fun notifyMediaStore(
        path: String,
        title: String,
        artist: String,
        album: String,
        genre: String = "",
        trackNumber: Int = 0,
        discNumber: Int = 1
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
            if (genre.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                put(MediaStore.Audio.Media.GENRE, genre)
            }
            if (trackNumber > 0) {
                val encoded = if (discNumber > 1) discNumber * 1000 + trackNumber else trackNumber
                put(MediaStore.Audio.Media.TRACK, encoded)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && trackNumber > 0) {
                put(MediaStore.Audio.Media.CD_TRACK_NUMBER, trackNumber.toString())
            }
        }
        val mediaUri = findMediaStoreUri(path)
        if (mediaUri != null) {
            try { context.contentResolver.update(mediaUri, values, null, null) }
            catch (e: Exception) { Log.w(TAG, "[notifyMediaStore] pre-scan update falló: ${e.message}") }
        }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, uri ->
            if (uri != null) {
                try { context.contentResolver.update(uri, values, null, null) }
                catch (e: Exception) { Log.w(TAG, "[notifyMediaStore] post-scan update falló: ${e.message}") }
            }
        }
    }

    /**
     * Busca el URI de MediaStore para un path físico dado.
     * Itera sobre todos los volúmenes externos (útil para tarjetas SD en Android 10+).
     */
    private fun findMediaStoreUri(path: String): Uri? {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection  = "${MediaStore.Audio.Media.DATA} = ?"
        val volumes    = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.getExternalVolumeNames(context)
        else
            setOf("external")

        for (volume in volumes) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Audio.Media.getContentUri(volume)
            else
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            context.contentResolver.query(
                collection, projection, selection, arrayOf(path), null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    return android.content.ContentUris.withAppendedId(collection, id)
                }
            }
        }
        return null
    }
}