/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.model.shapes.ShapeType

/**
 * Test if a string is a valid Kotlin identifier name
 */
fun isValidKotlinIdentifier(s: String): Boolean {
    val c = s.firstOrNull() ?: return false
    return when (c) {
        in 'a'..'z', in 'A'..'Z', '_' -> true
        else -> false
    }
}

internal fun ShapeType.isContainerType(): Boolean =
    when (this) {
        ShapeType.BLOB,
        ShapeType.BOOLEAN,
        ShapeType.STRING,
        ShapeType.TIMESTAMP,
        ShapeType.BYTE,
        ShapeType.SHORT,
        ShapeType.INTEGER,
        ShapeType.LONG,
        ShapeType.FLOAT,
        ShapeType.DOCUMENT,
        ShapeType.DOUBLE,
        ShapeType.BIG_DECIMAL,
        ShapeType.BIG_INTEGER,
        ShapeType.STRUCTURE,
        ShapeType.UNION, -> false
        ShapeType.LIST,
        ShapeType.SET,
        ShapeType.MAP,
        ShapeType.SERVICE,
        ShapeType.RESOURCE,
        ShapeType.OPERATION,
        ShapeType.MEMBER -> true
    }
