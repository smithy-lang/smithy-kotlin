/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

public enum class ShapeType {
    // simple
    BLOB,
    BOOLEAN,
    STRING,
    TIMESTAMP,
    BYTE,
    SHORT,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    BIG_INTEGER,
    BIG_DECIMAL,
    DOCUMENT,
    ENUM,
    INT_ENUM,

    // aggregate
    LIST,
    MAP,
    STRUCTURE,
    UNION,
    MEMBER,

    // service
    SERVICE,
    OPERATION,
    ;

    public val isSimple: Boolean
        get() = when (this) {
            BLOB, BOOLEAN, STRING, TIMESTAMP, BYTE, SHORT, INTEGER, LONG,
            FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL, DOCUMENT, ENUM, INT_ENUM,
            -> true
            else -> false
        }
}
