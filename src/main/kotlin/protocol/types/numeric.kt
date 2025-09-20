package org.redlin.protocol.types

import java.math.BigInteger
import org.redlin.protocol.CRLF
import org.redlin.protocol.Deserializer
import org.redlin.protocol.Serializer

object IntegerType : SimpleType<Long, Long> {
    override val firstByte
        get() = ':'

    override val serialize: Serializer<Long> = { data -> "$firstByte${data}$CRLF".toByteArray() }

    override val deserialize: Deserializer<Long> = { bytes ->
        String(bytes, 1, length(bytes) - 1).toLong()
    }
}

object DoubleType : SimpleType<Double, Double> {
    override val firstByte
        get() = ','

    override val serialize: Serializer<Double> = { data ->
        when {
            data.isNaN() -> "nan"
            data == Double.POSITIVE_INFINITY -> "inf"
            data == Double.NEGATIVE_INFINITY -> "-inf"
            else -> data.toString()
        }.let { "$firstByte$it$CRLF".toByteArray() }
    }
    override val deserialize: Deserializer<Double> = { bytes ->
        when (bytes.elementAt(2)) {
            'n'.code.toByte() -> Double.POSITIVE_INFINITY
            'i'.code.toByte() -> Double.NEGATIVE_INFINITY
            'a'.code.toByte() -> Double.NaN
            else -> String(bytes, 1, length(bytes) - 1).toDouble()
        }
    }
}

object BigNumberType : SimpleType<BigInteger, BigInteger> {
    override val firstByte
        get() = '('

    override val serialize: Serializer<BigInteger> = { data ->
        "$firstByte$data$CRLF".toByteArray()
    }
    override val deserialize: Deserializer<BigInteger> = { bytes ->
        String(bytes, 1, length(bytes) - 1).toBigInteger()
    }
}
