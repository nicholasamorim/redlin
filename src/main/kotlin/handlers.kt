package org.redlin

internal fun handlePing(req: Request, ctx: ConnectionContext) {
    when (req.args.size) {
        0 -> ctx.writer.string("PONG")
        1 -> ctx.writer.bulkString(req.args[0])
        else -> ctx.writer.error("wrong number of arguments for 'ping' command")
    }
}
