package org.redlin

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import org.redlin.protocol.CRLF
import org.redlin.protocol.types.SimpleError
import org.redlin.protocol.types.SimpleErrorType

/* ---------- Minimal RESP helpers (just enough for PING) ---------- */

/**
 * Reads **one Redis command name** from the socket and returns it as an uppercase-friendly
 * `String`.
 *
 * This understands two input formats:
 * 1) **RESP Arrays** (modern): `*<n>\r\n$<len>\r\n<bytes>\r\n ...`
 *     - Example (PING): `*1\r\n$4\r\nPING\r\n` → returns `"PING"`.
 *     - We **only** read the first bulk string (the command name) and skip any extra args so the
 *       stream stays aligned for the next read. (PING [message] is ignored for now.)
 * 2) **Inline protocol** (legacy / debugging): a single line separated by spaces
 *     - Example: `PING\r\n` → returns `"PING"`.
 *
 * Return value:
 * - `String` command name (e.g., "PING") if a full command was read.
 * - `null` if the peer closed the connection cleanly (EOF) or the data was malformed such that we
 *   can’t continue.
 *
 * Blocking behavior:
 * - This call **blocks** until enough bytes are available to decide what to return or until EOF.
 *
 * Stream hygiene:
 * - For RESP arrays, we **consume** any leftover arguments using [skipRespEntity] so that future
 *   reads start at a clean boundary.
 */
internal fun readCommand(input: BufferedInputStream): String? {
    input.mark(1)
    val first = input.read()
    if (first == -1) return null

    return when (first.toChar()) {
        '*' -> { // RESP Array: *<n>\r\n$<len>\r\n<bytes>\r\n ...
            val count = readNumberLine(input)
            if (count <= 0) return null
            // Expect first bulk string to be the command
            val cmd = readBulkString(input) ?: return null
            // Ignore additional args for now (keeps protocol state aligned)
            repeat(count - 1) { _ -> skipRespEntity(input) }
            cmd
        }
        else -> {
            // Inline protocol (legacy): we already consumed one byte – put it back into the line
            input.reset()
            val line = readLineCRLF(input) ?: return null
            line.trim().split(' ', '\t').firstOrNull() ?: ""
        }
    }
}

internal fun writeSimpleError(out: BufferedOutputStream, message: String) {
    out.write(SimpleErrorType.serialize(SimpleError(message = message)))
}

/** :<number>\r\n */
internal fun writeInteger(out: BufferedOutputStream, n: Long) =
    out.write((":$n$CRLF").toByteArray(UTF_8))

/** $<len>\r\n<bytes>\r\n */
internal fun writeBulkString(out: BufferedOutputStream, s: String) =
    out.write(("$${s.toByteArray(UTF_8).size}$CRLF$s$CRLF").toByteArray(UTF_8))

/** $-1\r\n (RESP Null Bulk String) */
internal fun writeNullBulkString(out: BufferedOutputStream) =
    out.write("\$-1$CRLF".toByteArray(UTF_8))

/** *<n>\r\n (array header; useful when you compose arrays manually) */
internal fun writeArrayHeader(out: BufferedOutputStream, n: Int) =
    out.write(("*$n$CRLF").toByteArray(UTF_8))

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
