package com.example.zyncwave2.data

/**
 * Parser de formato LRC.
 * Convierte texto como:
 *   [00:13.45] Get it on, get it on top
 *   [00:16.14] Make a pose
 * en una lista de LrcLine con timestamp en milisegundos y texto.
 */
object LrcParser {

    data class LrcLine(
        val timeMs: Long,   // timestamp en milisegundos
        val text: String    // texto de la línea
    )

    // Regex que captura [mm:ss.xx] o [mm:ss.xxx]
    private val LRC_REGEX = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$""")

    /**
     * Parsea el texto LRC completo.
     * Si el texto no tiene timestamps válidos, retorna lista vacía
     * para que la UI lo trate como letras planas.
     */
    fun parse(lrc: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()

        lrc.lines().forEach { line ->
            val match = LRC_REGEX.find(line.trim()) ?: return@forEach
            val (minStr, secStr, msStr, text) = match.destructured

            val minutes = minStr.toLongOrNull() ?: return@forEach
            val seconds = secStr.toLongOrNull() ?: return@forEach
            // Normalizar a ms: si son 2 dígitos → ×10, si son 3 → directo
            val millis  = when (msStr.length) {
                2    -> msStr.toLongOrNull()?.times(10) ?: return@forEach
                3    -> msStr.toLongOrNull() ?: return@forEach
                else -> return@forEach
            }

            val timeMs = minutes * 60_000L + seconds * 1_000L + millis
            val cleanText = text.trim()

            // Ignorar líneas de metadatos vacías o con info del archivo
            if (cleanText.isNotBlank()) {
                lines.add(LrcLine(timeMs = timeMs, text = cleanText))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * Detecta si un texto tiene formato LRC (al menos una línea con timestamp).
     */
    fun isLrc(text: String): Boolean =
        text.lines().any { LRC_REGEX.containsMatchIn(it.trim()) }

    /**
     * Encuentra el índice de la línea activa dado el tiempo actual en ms.
     * Retorna el índice de la última línea cuyo timestamp ≤ elapsedMs.
     */
    fun activeIndex(lines: List<LrcLine>, elapsedMs: Long): Int {
        if (lines.isEmpty()) return -1
        var result = 0
        for (i in lines.indices) {
            if (lines[i].timeMs <= elapsedMs) result = i else break
        }
        return result
    }
}