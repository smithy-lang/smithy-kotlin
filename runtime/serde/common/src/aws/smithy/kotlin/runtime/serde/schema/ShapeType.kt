/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

/**
 * The kind of a Smithy shape. Mirrors the Smithy data model: simple shapes, aggregate shapes,
 * the member relationship, and the service/operation service-model shapes.
 */
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

    /**
     * True if this type is one of the Smithy simple shapes (not an aggregate, member, or service shape).
     */
    public val isSimple: Boolean
        get() = when (this) {
            BLOB, BOOLEAN, STRING, TIMESTAMP, BYTE, SHORT, INTEGER, LONG,
            FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL, DOCUMENT, ENUM, INT_ENUM,
            -> true
            else -> false
        }
}
