package org.redlin.commands

import org.redlin.ConnectionContext
import org.redlin.Request
import org.redlin.storage.StorageManager
import org.redlin.storage.StorageResult
import org.redlin.storage.WriteMode

enum class SetStatus {
    WRITTEN,
    NOT_WRITTEN,
}

internal fun handleSet(req: Request, ctx: ConnectionContext, storage: StorageManager) {
    if (req.args.size < 2) {
        ctx.writer.error("wrong number of arguments for 'set' command")
        return
    }

    val key = req.args[0]
    val value = req.args[1].toByteArray(Charsets.UTF_8)

    val opts =
        when (val parsed = parseSetOptions(req.args)) {
            is ParseResult.Ok -> parsed.options
            is ParseResult.Err -> {
                ctx.writer.error(parsed.message)
                return
            }
        }

    when (val res = storage.set(key, value, opts.ttlMillis, opts.mode)) {
        is StorageResult.Ok ->
            when (res.value) {
                SetStatus.WRITTEN -> ctx.writer.string("OK")
                SetStatus.NOT_WRITTEN -> ctx.writer.nullBulkString()
            }
        is StorageResult.Error -> ctx.writer.error("internal error")
        StorageResult.NotFound -> ctx.writer.error("internal error")
    }
}

private data class SetOptions(val ttlMillis: Long? = null, val mode: WriteMode = WriteMode.UPSERT)

private sealed class ParseResult {
    data class Ok(val options: SetOptions) : ParseResult()

    data class Err(val message: String) : ParseResult()
}

private fun parseSetOptions(args: List<String>): ParseResult {
    var ttlMillis: Long? = null
    var mode: WriteMode? = null

    var i = 2
    while (i < args.size) {
        when (val token = args[i].uppercase()) {
            "EX",
            "PX" -> {
                if (i + 1 >= args.size) return ParseResult.Err("syntax error")
                if (ttlMillis != null)
                    return ParseResult.Err("syntax error") // EX and PX are mutually exclusive

                val raw =
                    args[i + 1].toLongOrNull()
                        ?: return ParseResult.Err("value is not an integer or out of range")
                if (raw <= 0) return ParseResult.Err("value is not an integer or out of range")

                ttlMillis = if (token == "EX") raw * 1000L else raw
                i += 2
            }
            "NX" -> {
                if (mode != null) return ParseResult.Err("syntax error") // NX vs XX conflict
                mode = WriteMode.ONLY_IF_ABSENT
                i += 1
            }
            "XX" -> {
                if (mode != null) return ParseResult.Err("syntax error") // NX vs XX conflict
                mode = WriteMode.ONLY_IF_PRESENT
                i += 1
            }
            else -> return ParseResult.Err("syntax error")
        }
    }

    return ParseResult.Ok(SetOptions(ttlMillis = ttlMillis, mode = mode ?: WriteMode.UPSERT))
}
