/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.Trait

public sealed interface MapSchema : Schema {
    public val key: MemberSchema
    public val value: MemberSchema
}

internal class MapSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: Collection<Trait>,
    override val key: MemberSchema,
    override val value: MemberSchema,
) : MapSchema {
    override val type: ShapeType = ShapeType.MAP
    override fun toString(): String = "MapSchema($shapeId)"
}

/**
 * Create a [MapSchema] whose key is [key] and value is [value].
 */
public fun MapSchema(id: ShapeId, key: MemberSchema, value: MemberSchema, vararg traits: Trait): MapSchema {
    val expectedKey = id.withMember("key")
    val expectedValue = id.withMember("value")
    require(key.shapeId == expectedKey) { "map $id key must be $expectedKey, was ${key.shapeId}" }
    require(value.shapeId == expectedValue) { "map $id value must be $expectedValue, was ${value.shapeId}" }
    return MapSchemaImpl(id, traits.toList(), key, value)
}
