package org.redlin.protocol

typealias Serializer<S> = (S) -> ByteArray

typealias Deserializer<D> = (ByteArray) -> D
