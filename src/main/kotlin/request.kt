package org.redlin

data class ConnectionContext(val writer: ProtocolWriter)

data class Request(val command: String, val args: List<String>)

fun interface RequestHandler {
    fun handle(req: Request, ctx: ConnectionContext)
}

object CommandManager {
    private val handlers: Map<String, RequestHandler> =
        mapOf(
            "PING" to RequestHandler(::handlePing)
            // Add more: "ECHO" to CommandHandler(::handleEcho), "SET" to ::handleSet, etc.
        )

    fun process(req: Request, context: ConnectionContext) {
        val h = handlers[req.command.uppercase()]
        if (h == null) {
            context.writer.error("unknown command '${req.command.lowercase()}'")
            return
        }
        h.handle(req, context)
    }
}
