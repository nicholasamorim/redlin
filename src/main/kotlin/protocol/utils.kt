package org.redlin.protocol

import java.math.BigInteger
import org.redlin.protocol.types.*

const val CRLF = "\r\n"
const val CRLF_FIRST_BYTE = '\r'.code.toByte()
const val USE_BULK_STRING = true

internal fun ByteArray.lengthUntilCRLF() = this.indexOf(CRLF_FIRST_BYTE)

fun ByteArray.toDataType(): DataType<out Any?, out Any?> {
    if (isEmpty()) throw IllegalArgumentException("Empty data")

    val firstByte = this[0].toInt()
    val dataType =
        dataTypeMap[firstByte]
            ?: throw IllegalArgumentException("Data type not found for $firstByte")

    return dataType
}

/**
 * Returns a length of the data.
 *
 * For Bulk types, the length is the number of bytes of the actual data. For Aggregate types, the
 * length is the number of elements the data contains.
 */
internal fun ByteArray.length(): Int {
    var len = 0
    var i = 1

    while (this[i] != CRLF.toByte()) {
        len = len * 10 + (this[i] - '0'.code)
        i++
    }

    return len
}

/**
 * Deserializes the data from a byte array. It returns a pair of the deserialized data and the total
 * length of the serialized data.
 */
private fun deserializeElement(data: ByteArray) =
    when (val dataType = data.toDataType()) {
        is SimpleType -> {
            val len = dataType.length(data) + CRLF.length
            dataType.deserialize(data.sliceArray(0..<len)) to len
        }

        is BulkType -> {
            val len = data.lengthUntilCRLF() + dataType.length(data) + CRLF.length * 2
            dataType.deserialize(data.sliceArray(0..<len)) to len
        }

        is AggregateType ->
            when (dataType) {
                is ArrayType -> deserializeArray(data, mutableListOf())
            }

        else -> {
            throw IllegalArgumentException("Data type not found for $dataType")
        }
    }

/**
 * Serializes the data to a byte array.
 *
 * It serializes all elements of the container and collects them into a single byte array by calling
 * [serializeContainer] recursively.
 *
 * @param data the data to serialize
 * @return the serialized data
 */
internal fun serializeContainer(data: Any?): ByteArray =
    when (data) {
        is String ->
            when (USE_BULK_STRING) {
                true -> BulkStringType.serialize(data)
                false ->
                    when (data.contains('\r') || data.contains('\n')) {
                        true -> BulkStringType.serialize(data)
                        false -> SimpleStringType.serialize(data)
                    }
            }

        is SimpleError -> SimpleErrorType.serialize(data)
        is Int -> IntegerType.serialize(data.toLong())
        is Long -> IntegerType.serialize(data)
        is List<*> ->
            when (data.isEmpty()) {
                true -> "${ArrayType.firstByte}0$CRLF".toByteArray()
                false -> {
                    val collect = data.map { serializeContainer(it) }
                    collect.fold(
                        "${ArrayType.firstByte}${collect.size}$CRLF".toByteArray(),
                        ByteArray::plus,
                    )
                }
            }

        is Boolean -> BooleanType.serialize(data)
        is Double -> DoubleType.serialize(data)
        is BigInteger -> BigNumberType.serialize(data)
        is BulkError -> BulkErrorType.serialize(data)
        else ->
            when (data) {
                null -> NullType.serialize(null)
                else -> throw IllegalArgumentException("Unknown data type: $data")
            }
    }

internal fun <T : MutableCollection<Any?>> deserializeArray(
    data: ByteArray,
    container: T,
): Pair<T, Int> {
    val numOfElements = data.length()
    val prefix = data.lengthUntilCRLF() + CRLF.length

    var round = data.sliceArray(prefix..<data.size)
    var count = 0
    var totalLength = prefix

    while (count < numOfElements) {
        count++

        val (element, len) = deserializeElement(round)

        container.add(element)
        totalLength += len

        if (count == numOfElements) {
            break
        }

        round = round.sliceArray(len..<round.size)
    }

    return container to totalLength
}
