/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

public enum class ShapeType(public val isSimple: Boolean) {
    // simple
    BLOB(true),
    BOOLEAN(true),
    STRING(true),
    TIMESTAMP(true),
    BYTE(true),
    SHORT(true),
    INTEGER(true),
    LONG(true),
    FLOAT(true),
    DOUBLE(true),
    BIG_INTEGER(true),
    BIG_DECIMAL(true),
    DOCUMENT(true),
    ENUM(true),
    INT_ENUM(true),

    // aggregate
    LIST(false),
    MAP(false),
    STRUCTURE(false),
    UNION(false),
    MEMBER(false),

    // service
    SERVICE(false),
    OPERATION(false),
}
