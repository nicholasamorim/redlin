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

sealed interface ErrorType : Deserializer<Error>

data class SimpleError(override val prefix: String, override val message: String) : Error {
  init {
    require(!message.contains('\r') && !message.contains('\n')) {
      "Error cannot contain '\\r' or '\\n'"
    }
  }
}

data class BulkError(override val prefix: String, override val message: String) : Error

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

object SimpleStringType : SimpleType<String, String> {
  override val firstByte
    get() = '+'

  override val serialize: Serializer<String> = { data -> "$firstByte$data$CRLF".toByteArray() }

  override val deserialize: Deserializer<String> = { bytes -> String(bytes, 1, length(bytes) - 1) }
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

object IntegerType : SimpleType<Long, Long> {
  override val firstByte
    get() = ':'

  override val serialize: Serializer<Long> = { data -> "$firstByte${data}$CRLF".toByteArray() }

  override val deserialize: Deserializer<Long> = { bytes ->
    String(bytes, 1, length(bytes) - 1).toLong()
  }
}

object BooleanType : SimpleType<Boolean, Boolean> {
  override val firstByte
    get() = '#'

  override val serialize: Serializer<Boolean> = { data ->
    "$firstByte${if (data) "t" else "f"}$CRLF".toByteArray()
  }

  override val deserialize: Deserializer<Boolean> = { bytes -> bytes[1].toInt() == 't'.code }
}

internal val dataTypeMap =
    mapOf(
        SimpleStringType.firstByte.code to SimpleStringType,
        IntegerType.firstByte.code to IntegerType,
        BooleanType.firstByte.code to BooleanType,
        NullType.firstByte.code to NullType,
        BulkStringType.firstByte.code to BulkStringType,
        SimpleErrorType.firstByte.code to SimpleErrorType,
        BulkErrorType.firstByte.code to BulkErrorType,
    )
