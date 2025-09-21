package org.redlin

import org.redlin.commands.*
import org.redlin.storage.StorageManager

data class ConnectionContext(val writer: ProtocolWriter)

data class Request(val command: String, val args: List<String>)

fun interface RequestHandler {
    fun handle(req: Request, ctx: ConnectionContext, storage: StorageManager)
}

object CommandManager {
    private val handlers: Map<String, RequestHandler> =
        mapOf(
            "PING" to RequestHandler(::handlePing),
            "ECHO" to RequestHandler(::handleEcho),
            "SET" to RequestHandler(::handleSet),
            "GET" to RequestHandler(::handleGet),
        )

    fun process(req: Request, context: ConnectionContext, storage: StorageManager) {
        val h = handlers[req.command.uppercase()]
        if (h == null) {
            context.writer.error("unknown command '${req.command.lowercase()}'")
            return
        }
        h.handle(req, context, storage)
    }
}
