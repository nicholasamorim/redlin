package org.redlin.commands

import org.redlin.ConnectionContext
import org.redlin.Request
import org.redlin.storage.StorageManager

internal fun handleEcho(req: Request, ctx: ConnectionContext, storage: StorageManager) {
    when (req.args.size) {
        1 -> ctx.writer.string(req.args[0])
        else -> ctx.writer.error("wrong number of arguments for 'echo' command")
    }
}
