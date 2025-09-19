package org.redlin.protocol

typealias Serializer<S> = (S) -> ByteArray
typealias Deserializer<D> = (ByteArray) -> D

sealed interface DataType<S, D> {
    val firstByte: Char
    val length: (ByteArray) -> Int

    val serialize: Serializer<S>
    val deserialize: Deserializer<D>
}

sealed interface Error {
    val prefix: String
    val message: String
}

data class SimpleError(
    override val prefix: String, override val message: String
) : Error {
    init {
        require(!message.contains('\r') && !message.contains('\n')) {
            "Error cannot contain '\\r' or '\\n'"
        }
    }
}

interface SimpleType<S, D> : DataType<S, D> {
    override val length: (ByteArray) -> Int
        get() = { it.lengthUntilCRLF() }
}

object NullType : SimpleType<Nothing?, Nothing?> {
    override val firstByte get() = '_'

    override val serialize: Serializer<Nothing?> = {
        "$firstByte$CRLF".toByteArray()
    }

    override val deserialize: Deserializer<Nothing?> = { null }
}

object SimpleStringType : SimpleType<String, String> {
    override val firstByte get() = '+'

    override val serialize: Serializer<String> = { data ->
        "$firstByte$data$CRLF".toByteArray()
    }

    override val deserialize: Deserializer<String> = { bytes ->
        String(bytes, 1, length(bytes) - 1)
    }
}

object IntegerType : SimpleType<Long, Long> {
    override val firstByte get() = ':'

    override val serialize: Serializer<Long> = { data ->
        "$firstByte${data}$CRLF".toByteArray()
    }

    override val deserialize: Deserializer<Long> = { bytes ->
        String(bytes, 1, length(bytes) - 1).toLong()
    }
}

object BooleanType : SimpleType<Boolean, Boolean> {
    override val firstByte get() = '#'

    override val serialize: Serializer<Boolean> = { data ->
        "$firstByte${if (data) "t" else "f"}$CRLF".toByteArray()
    }

    override val deserialize: Deserializer<Boolean> = { bytes ->
        bytes[1].toInt() == 't'.code
    }
}

internal val dataTypeMap = mapOf(
    SimpleStringType.firstByte.code to SimpleStringType,
    IntegerType.firstByte.code to IntegerType,
    BooleanType.firstByte.code to BooleanType,
    NullType.firstByte.code to NullType,
)