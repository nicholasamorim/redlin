package org.redlin.protocol.types

import org.redlin.protocol.CRLF
import org.redlin.protocol.Deserializer
import org.redlin.protocol.Serializer

sealed interface Error {
    val prefix: String
    val message: String
}

sealed interface ErrorType : Deserializer<Error>

data class SimpleError(override val prefix: String = "ERR", override val message: String) : Error {
    init {
        require(!message.contains('\r') && !message.contains('\n')) {
            "Error cannot contain '\\r' or '\\n'"
        }
    }
}

object SimpleErrorType : SimpleType<SimpleError, Error>, ErrorType {
    override val firstByte
        get() = '-'

    override operator fun invoke(bytes: ByteArray): Error = deserialize(bytes)

    override val serialize: Serializer<SimpleError> = { data ->
        "$firstByte${data.prefix} ${data.message}$CRLF".toByteArray()
    }

    override val deserialize: Deserializer<SimpleError> = { bytes ->
        SimpleStringType.deserialize(bytes).let {
            val split = it.split(" ", limit = 2)
            SimpleError(split[0], split[1])
        }
    }
}

data class BulkError(override val prefix: String, override val message: String) : Error

object BulkErrorType : BulkType<BulkError, Error>, ErrorType {
    override val firstByte
        get() = '!'

    override operator fun invoke(bytes: ByteArray): Error = deserialize(bytes)

    override val serialize: Serializer<BulkError> = { data ->
        "${data.prefix} ${data.message}".let { "$firstByte${it.length}$CRLF$it$CRLF".toByteArray() }
    }

    override val deserialize: Deserializer<BulkError> = { bytes ->
        BulkStringType.deserialize(bytes).let {
            val split = it.split(" ", limit = 2)
            BulkError(split[0], split[1])
        }
    }
}
