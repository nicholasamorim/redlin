package org.redlin

import java.io.BufferedOutputStream
import org.redlin.protocol.types.BulkStringType
import org.redlin.protocol.types.SimpleStringType

data class Request(val command: String, val args: List<String>)

fun interface CommandHandler {
    fun handle(req: Request, out: BufferedOutputStream)
}

private fun handlePing(req: Request, out: BufferedOutputStream) {
    when (req.args.size) {
        0 -> out.write(SimpleStringType.serialize("PONG"))
        1 -> out.write(BulkStringType.serialize(req.args[0]))

        else -> writeSimpleError(out, "wrong number of arguments for 'ping' command")
    }
}

object CommandManager {
    private val handlers: Map<String, CommandHandler> =
        mapOf(
            "PING" to CommandHandler(::handlePing)
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
