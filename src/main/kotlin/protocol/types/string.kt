package org.redlin.protocol.types

import org.redlin.protocol.CRLF
import org.redlin.protocol.Deserializer
import org.redlin.protocol.Serializer
import org.redlin.protocol.lengthUntilCRLF

object SimpleStringType : SimpleType<String, String> {
    override val firstByte
        get() = '+'

    override val serialize: Serializer<String> = { data -> "$firstByte$data$CRLF".toByteArray() }

    override val deserialize: Deserializer<String> = { bytes ->
        String(bytes, 1, length(bytes) - 1)
    }
}

object BulkStringType : BulkType<String, String> {
    override val firstByte
        get() = '$'

    override val serialize: Serializer<String> = { data ->
        "$firstByte${data.length}$CRLF$data$CRLF".toByteArray()
    }

    override val deserialize: Deserializer<String> = { bytes ->
        String(bytes, bytes.lengthUntilCRLF() + CRLF.length, length(bytes))
    }
}
