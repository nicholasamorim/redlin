package org.redlin

import java.io.BufferedOutputStream

data class Request(val command: String, val args: List<String>)

fun interface RequestHandler {
    fun handle(req: Request, out: BufferedOutputStream)
}

object CommandManager {
    private val handlers: Map<String, RequestHandler> =
        mapOf(
            "PING" to RequestHandler(::handlePing)
            // Add more: "ECHO" to CommandHandler(::handleEcho), "SET" to ::handleSet, etc.
        )

    fun process(req: Request, out: BufferedOutputStream) {
        val h = handlers[req.command.uppercase()]
        if (h == null) {
            writeSimpleError(out, "unknown command '${req.command.lowercase()}'")
            return
        }
        h.handle(req, out)
    }
}
