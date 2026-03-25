/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

public interface Shape {
    public val shapeId: ShapeId
    public val traits: List<Trait>
}

public enum class ShapeType {
    BIG_DECIMAL,
    BIG_INTEGER,
    BLOB,
    BOOLEAN,
    BYTE,
    DOCUMENT,
    DOUBLE,
    ENUM,
    FLOAT,
    INTEGER,
    INTEGER_ENUM,
    LIST,
    LONG,
    MAP,
    MEMBER,
    OPERATION,
    SERVICE,
    SET,
    SHORT,
    STRING,
    TIMESTAMP,
    STRUCTURE,
    UNION,
}
