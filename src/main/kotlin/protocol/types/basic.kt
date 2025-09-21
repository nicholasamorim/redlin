package org.redlin.protocol.types

import org.redlin.protocol.CRLF
import org.redlin.protocol.Deserializer
import org.redlin.protocol.Serializer
import org.redlin.protocol.length
import org.redlin.protocol.lengthUntilCRLF

interface DataType<S, D> {
    val firstByte: Char
    val length: (ByteArray) -> Int

    val serialize: Serializer<S>
    val deserialize: Deserializer<D>
}

interface SimpleType<S, D> : DataType<S, D> {
    override val length: (ByteArray) -> Int
        get() = { it.lengthUntilCRLF() }
}

interface BulkType<S, D> : DataType<S, D> {
    override val length: (ByteArray) -> Int
        get() = { it.length() }
}

object NullType : SimpleType<Nothing?, Nothing?> {
    override val firstByte
        get() = '_'

    override val serialize: Serializer<Nothing?> = { "$firstByte$CRLF".toByteArray() }

    override val deserialize: Deserializer<Nothing?> = { null }
}

object BooleanType : SimpleType<Boolean, Boolean> {
    override val firstByte
        get() = '#'

    override val serialize: Serializer<Boolean> = { data ->
        "$firstByte${if (data) "t" else "f"}$CRLF".toByteArray()
    }

    override val deserialize: Deserializer<Boolean> = { bytes -> bytes[1].toInt() == 't'.code }
}
