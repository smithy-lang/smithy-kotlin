/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.Trait

public sealed interface SimpleSchema : Schema

internal class SimpleSchemaImpl(
    override val shapeId: ShapeId,
    override val type: ShapeType,
    override val traits: Collection<Trait>,
) : SimpleSchema {
    init {
        require(type.isSimple) { "$type is not a simple shape type" }
    }
    override fun toString(): String = "SimpleSchema($shapeId: $type)"
}

public fun SimpleSchema(id: ShapeId, type: ShapeType, vararg traits: Trait): SimpleSchema = SimpleSchemaImpl(id, type, traits.toList())
