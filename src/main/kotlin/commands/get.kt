package org.redlin.commands

import kotlin.collections.toString
import org.redlin.ConnectionContext
import org.redlin.Request
import org.redlin.storage.StorageManager
import org.redlin.storage.StorageResult

internal fun handleGet(req: Request, ctx: ConnectionContext, storage: StorageManager) {
    if (req.args.size != 1) {
        ctx.writer.error("wrong number of arguments for 'get' command")
        return
    }

    val key = req.args[0]

    when (val res = storage.get(key)) {
        is StorageResult.Ok -> {
            ctx.writer.bulkString(res.value.toString(Charsets.UTF_8))
        }
        StorageResult.NotFound -> ctx.writer.nullBulkString()
        is StorageResult.Error -> ctx.writer.error("internal error")
    }
}
