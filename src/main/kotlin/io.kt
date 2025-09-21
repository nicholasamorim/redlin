package org.redlin

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import org.redlin.protocol.types.BulkStringType
import org.redlin.protocol.types.SimpleError
import org.redlin.protocol.types.SimpleErrorType
import org.redlin.protocol.types.SimpleStringType

interface ProtocolWriter {
    fun string(s: String)

    fun bulkString(s: String)

    fun nullBulkString()

    fun error(message: String)

    fun flush()
}

class BufferedProtocolWriter(private val out: BufferedOutputStream) : ProtocolWriter {
    override fun string(s: String) = out.write(SimpleStringType.serialize(s))

    override fun bulkString(s: String) = out.write(BulkStringType.serialize(s))

    override fun nullBulkString() = out.write("\$-1\r\n".toByteArray(Charsets.UTF_8))

    override fun error(message: String) =
        out.write(SimpleErrorType.serialize(SimpleError(message = message)))

    override fun flush() = out.flush()
}

/**
 * Reads one RESP request (array of bulk strings, or inline) and returns the command + args. Returns
 * null on EOF or malformed input.
 */
internal fun readRequest(input: BufferedInputStream): Request? {
    input.mark(1)
    val first = input.read()
    if (first == -1) return null

    return when (first.toChar()) {
        // RESP array: *<n>\r\n $<len>\r\n <bytes>\r\n ...
        '*' -> {
            val count = readNumberLine(input)
            if (count <= 0) return null
            val parts = ArrayList<String>(count)
            repeat(count) {
                val s =
                    readBulkString(input)
                        ?: return null // expect bulk strings (what redis-cli sends)
                parts += s
            }
            if (parts.isEmpty()) return null
            Request(parts[0], parts.drop(1))
        }
        else -> {
            // Inline protocol: COMMAND arg1 arg2\r\n
            input.reset()
            val line = readLineCRLF(input) ?: return null
            val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            Request(tokens[0], tokens.drop(1))
        }
    }
}

/* ---------- RESP parsing utilities ---------- */

/**
 * Reads bytes until it encounters the **CRLF** sequence (`\r\n`) and returns the line **without**
 * the trailing CRLF.
 *
 * Example:
 * - Input bytes: `49 50 51 13 10` (i.e., `"123\r\n"`) → returns `"123"`.
 *
 * Returns:
 * - The line as `String`, or `null` if the stream ends before any byte is read.
 *
 * Subtleties:
 * - If EOF occurs **after** reading some bytes but **before** CRLF, we return the partial line
 *   (mirrors many simple parsers) so the caller can decide whether that’s acceptable.
 */
private fun readLineCRLF(input: BufferedInputStream): String? {
    val sb = StringBuilder()
    var prev = -1
    while (true) {
        val b = input.read()
        if (b == -1) return if (sb.isEmpty()) null else sb.toString()
        if (prev == '\r'.code && b == '\n'.code) {
            // drop the trailing \r
            sb.setLength(maxOf(0, sb.length - 1))
            return sb.toString()
        }
        sb.append(b.toChar())
        prev = b
    }
}

/**
 * Reads a CRLF-terminated line and parses it as an `Int`.
 *
 * Used for RESP length/count headers like:
 * - `*<n>\r\n` where `<n>` is the number of array elements.
 * - `$<len>\r\n` where `<len>` is the byte length of a bulk string.
 *
 * Returns:
 * - Parsed integer, or `-1` on EOF / malformed integer.
 *
 * Example:
 * - Input bytes: `"5\r\n"` → returns `5`.
 * - Input bytes: `"abc\r\n"` → returns `-1`.
 */
private fun readNumberLine(input: BufferedInputStream): Int {
    val line = readLineCRLF(input) ?: return -1
    return line.toIntOrNull() ?: -1
}

/**
 * Reads a RESP **Bulk String** and returns it as a `String`.
 *
 * Wire format:
 * ```
 * $<len>\r\n<bytes...>\r\n
 * ```
 * - `<len>` is the exact number of bytes to read next.
 * - After reading `<len>` bytes, we **must** consume the trailing `\r\n`.
 *
 * Returns:
 * - The decoded string (UTF-8 by default via `decodeToString()`), or `null` on EOF / malformed
 *   data.
 *
 * Example (command name "PING"):
 * - Input: `"$4\r\nPING\r\n"` → returns `"PING"`.
 *
 * Safety:
 * - If `<len>` is negative or the stream ends early, returns `null`.
 */
private fun readBulkString(input: BufferedInputStream): String? {
    val first = input.read()
    if (first != '$'.code) return null
    val len = readNumberLine(input)
    if (len < 0) return null
    val buf = ByteArray(len)
    var read = 0
    while (read < len) {
        val n = input.read(buf, read, len - read)
        if (n == -1) return null
        read += n
    }
    // consume trailing CRLF
    if (input.read() != '\r'.code || input.read() != '\n'.code) return null
    return buf.decodeToString()
}

/**
 * Skips **one RESP entity** (of most common types) without interpreting its content.
 *
 * Why this exists:
 * - When we only care about the **first** token (e.g., the command name in an array), we still need
 *   to consume the remaining entities to keep the stream aligned for subsequent commands on the
 *   same connection.
 *
 * Entities handled:
 * - Simple String: `+... \r\n` → read and discard line
 * - Error: `-... \r\n` → read and discard line
 * - Integer: `:... \r\n` → read and discard line
 * - Bulk String: `$<len>\r\n<bytes>\r\n` → skip `<len>` bytes + CRLF
 * - Array: `*<n>\r\n<entity>...` → recursively skip `n` nested entities
 * - Anything else: we try to resync by consuming a single line with [readLineCRLF].
 *
 * If the stream ends (`-1`) we simply return.
 *
 * Notes:
 * - This is intentionally **permissive**: it aims to keep the connection usable rather than fail
 *   hard on unexpected inputs (useful for an educational/early-stage server).
 */
private fun skipRespEntity(input: BufferedInputStream) {
    when (input.read()) {
        -1 -> return
        '+'.code,
        '-'.code,
        ':'.code -> {
            readLineCRLF(input)
        } // simple string / error / integer
        '$'.code -> {
            val len = readNumberLine(input)
            if (len >= 0) {
                var toSkip = len + 2 // plus CRLF
                while (toSkip > 0) {
                    val skipped = input.skip(toSkip.toLong()).toInt()
                    if (skipped <= 0) break
                    toSkip -= skipped
                }
            }
        }
        '*'.code -> {
            val n = readNumberLine(input)
            repeat(n.coerceAtLeast(0)) { skipRespEntity(input) }
        }
        else -> {
            /* unknown – try to resync by reading a line */
            readLineCRLF(input)
        }
    }
}
