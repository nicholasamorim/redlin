package org.redlin.protocol.types

import org.redlin.protocol.Deserializer
import org.redlin.protocol.Serializer
import org.redlin.protocol.deserializeArray
import org.redlin.protocol.length
import org.redlin.protocol.serializeContainer

sealed interface AggregateType<S, D> : DataType<S, D> {
    override val length: (ByteArray) -> Int
        get() = { it.length() }
}

object ArrayType : AggregateType<List<Any?>, List<Any?>> {
    override val firstByte
        get() = '*'

    override val serialize: Serializer<List<Any?>> = { data -> serializeContainer(data) }

    override val deserialize: Deserializer<List<Any?>> = { bytes ->
        deserializeArray(bytes, mutableListOf()).first.toList()
    }
}
