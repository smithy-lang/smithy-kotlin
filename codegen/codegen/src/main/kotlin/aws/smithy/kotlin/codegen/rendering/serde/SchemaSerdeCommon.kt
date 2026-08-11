/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType

internal fun String.screamingSnake(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase()

internal val MemberShape.constName: String
    get() = memberName.toCamelCase().screamingSnake()

internal val Shape.writeFn: String
    get() = when (type) {
        ShapeType.BOOLEAN -> "writeBoolean"
        ShapeType.BYTE -> "writeByte"
        ShapeType.SHORT -> "writeShort"
        ShapeType.INTEGER, ShapeType.INT_ENUM -> "writeInt"
        ShapeType.LONG -> "writeLong"
        ShapeType.FLOAT -> "writeFloat"
        ShapeType.DOUBLE -> "writeDouble"
        ShapeType.BIG_INTEGER -> "writeBigInteger"
        ShapeType.BIG_DECIMAL -> "writeBigDecimal"
        ShapeType.STRING, ShapeType.ENUM -> "writeString"
        ShapeType.BLOB -> "writeBlob"
        ShapeType.TIMESTAMP -> "writeTimestamp"
        ShapeType.DOCUMENT -> "writeDocument"
        else -> error("no write function for shape type $type")
    }

internal val Shape.readFn: String
    get() = when (type) {
        ShapeType.BOOLEAN -> "readBoolean"
        ShapeType.BYTE -> "readByte"
        ShapeType.SHORT -> "readShort"
        ShapeType.INTEGER, ShapeType.INT_ENUM -> "readInt"
        ShapeType.LONG -> "readLong"
        ShapeType.FLOAT -> "readFloat"
        ShapeType.DOUBLE -> "readDouble"
        ShapeType.BIG_INTEGER -> "readBigInteger"
        ShapeType.BIG_DECIMAL -> "readBigDecimal"
        ShapeType.STRING, ShapeType.ENUM -> "readString"
        ShapeType.BLOB -> "readBlob"
        ShapeType.TIMESTAMP -> "readTimestamp"
        ShapeType.DOCUMENT -> "readDocument"
        else -> error("no read function for shape type $type")
    }
