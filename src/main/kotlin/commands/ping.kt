package org.redlin.commands

import org.redlin.ConnectionContext
import org.redlin.Request
import org.redlin.storage.StorageManager

internal fun handlePing(req: Request, ctx: ConnectionContext, storage: StorageManager) {
    when (req.args.size) {
        0 -> ctx.writer.string("PONG")
        1 -> ctx.writer.bulkString(req.args[0])
        else -> ctx.writer.error("wrong number of arguments for 'ping' command")
    }
}
