package org.redlin

import java.io.BufferedOutputStream
import org.redlin.protocol.types.BulkStringType
import org.redlin.protocol.types.SimpleStringType

internal fun handlePing(req: Request, out: BufferedOutputStream) {
    when (req.args.size) {
        0 -> out.write(SimpleStringType.serialize("PONG"))
        1 -> out.write(BulkStringType.serialize(req.args[0]))

        else -> writeSimpleError(out, "wrong number of arguments for 'ping' command")
    }
}
