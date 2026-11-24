package app.trierarch.terminal

import java.io.File
import java.io.RandomAccessFile

private const val MAX_TAIL_BYTES = 256 * 1024
private val ANSI_ESCAPE = Regex("\u001B(?:\\[[0-9;?]*[ -/]*[@-~]|\\][^\u0007]*\u0007)")

/**
 * Reads the last [lines] lines of a raw session transcript. The head of a long
 * session is irrelevant and the file can be large, so only a bounded window at
 * the end is read. Returns null when there is nothing to show.
 */
internal fun tailSessionLog(file: File, lines: Int): String? {
    if (lines <= 0 || !file.isFile) return null
    val length = file.length()
    if (length == 0L) return null
    val window = minOf(length, MAX_TAIL_BYTES.toLong()).toInt()
    val bytes = ByteArray(window)
    val startsMidLine = RandomAccessFile(file, "r").use { access ->
        access.seek(length - window)
        access.readFully(bytes)
        if (window < length) {
            access.seek(length - window - 1)
            access.read() != '\n'.code
        } else {
            false
        }
    }
    var text = String(bytes, Charsets.UTF_8)
    if (startsMidLine) {
        val firstBreak = text.indexOf('\n')
        if (firstBreak < 0) return null
        text = text.substring(firstBreak + 1)
    }
    text = text.replace("\r", "").replace(ANSI_ESCAPE, "")
    val all = text.split('\n')
    val cleaned = if (all.isNotEmpty() && all.last().isEmpty()) all.dropLast(1) else all
    return cleaned.takeLast(lines).joinToString("\n").ifEmpty { null }
}
