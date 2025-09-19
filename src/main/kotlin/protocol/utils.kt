package org.redlin.protocol

const val CRLF = "\r\n"
const val CRLF_FIRST_BYTE = '\r'.code.toByte()

internal fun ByteArray.lengthUntilCRLF() = this.indexOf(CRLF_FIRST_BYTE)

fun ByteArray.toType(): DataType<out Any?, out Any?> {
    if (isEmpty()) throw IllegalArgumentException("Empty data")

    val firstByte = this[0].toInt()
    val dataType = dataTypeMap[firstByte]
        ?: throw IllegalArgumentException("Data type not found for $firstByte")

    return dataType
}

/**
 * Returns a length of the data.
 *
 * For Bulk types, the length is the number of bytes of the actual data.
 * For Aggregate types, the length is the number of elements the data contains.
 */
private fun ByteArray.length(): Int {
    var len = 0
    var i = 1

    while (this[i] != CRLF.toByte()) {
        len = len * 10 + (this[i] - '0'.code)
        i++
    }

    return len
}