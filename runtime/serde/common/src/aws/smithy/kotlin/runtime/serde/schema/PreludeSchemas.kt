/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

/**
 * Shared, trait-free [SimpleSchema] singletons for the Smithy prelude simple shapes (`smithy.api#*`).
 *
 * Members reference these directly (`member("name", PreludeSchemas.String)`) instead of allocating a
 * fresh simple schema per use, keeping generated schema code small and load-time allocation low.
 */
public object PreludeSchemas {
    private fun prelude(name: String, type: ShapeType): SimpleSchema = SimpleSchemaImpl(shapeId("smithy.api", name), type, emptyList())

    public val Blob: SimpleSchema = prelude("Blob", ShapeType.BLOB)
    public val Boolean: SimpleSchema = prelude("Boolean", ShapeType.BOOLEAN)
    public val String: SimpleSchema = prelude("String", ShapeType.STRING)
    public val Timestamp: SimpleSchema = prelude("Timestamp", ShapeType.TIMESTAMP)
    public val Byte: SimpleSchema = prelude("Byte", ShapeType.BYTE)
    public val Short: SimpleSchema = prelude("Short", ShapeType.SHORT)
    public val Integer: SimpleSchema = prelude("Integer", ShapeType.INTEGER)
    public val Long: SimpleSchema = prelude("Long", ShapeType.LONG)
    public val Float: SimpleSchema = prelude("Float", ShapeType.FLOAT)
    public val Double: SimpleSchema = prelude("Double", ShapeType.DOUBLE)
    public val BigInteger: SimpleSchema = prelude("BigInteger", ShapeType.BIG_INTEGER)
    public val BigDecimal: SimpleSchema = prelude("BigDecimal", ShapeType.BIG_DECIMAL)
    public val Document: SimpleSchema = prelude("Document", ShapeType.DOCUMENT)
}
