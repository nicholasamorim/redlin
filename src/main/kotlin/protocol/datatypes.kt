package org.redlin.protocol

import org.redlin.protocol.types.*

internal fun mapOfTypes(vararg types: DataType<*, *>): Map<Int, DataType<*, *>> =
    types.associateBy { it.firstByte.code }

internal val dataTypeMap =
    mapOfTypes(
        NullType,
        BooleanType,
        SimpleStringType,
        BulkStringType,
        IntegerType,
        DoubleType,
        BigNumberType,
        ArrayType,
        SimpleErrorType,
        BulkErrorType,
    )
