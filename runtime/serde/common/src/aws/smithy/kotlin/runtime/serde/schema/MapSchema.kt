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
    keyProvider: () -> MemberSchema,
    valueProvider: () -> MemberSchema,
) : MapSchema {
    override val type: ShapeType = ShapeType.MAP
    override val key: MemberSchema by lazy(keyProvider)
    override val value: MemberSchema by lazy(valueProvider)
    override fun toString(): String = "MapSchema($shapeId)"
}

@SchemaDsl
public class MapSchemaBuilder internal constructor(private val shapeId: ShapeId) {
    private val traits = mutableListOf<Trait>()
    private var key: (() -> MemberSchema)? = null
    private var value: (() -> MemberSchema)? = null

    /** Add a root-level trait. */
    public fun trait(trait: Trait) {
        traits.add(trait)
    }

    /** Declare the key with an eagerly-known [target]. */
    public fun key(target: Schema, vararg traits: Trait): Unit = key(lazyOf(target), *traits)

    /** Declare the key whose [target] is resolved lazily. */
    public fun key(target: Lazy<Schema>, vararg traits: Trait) {
        val memberTraits = traits.toList()
        key = {
            MemberSchemaImpl(
                shapeId = shapeId.withMember("key"),
                traits = memberTraits,
                targetProvider = { target.value },
            )
        }
    }

    /** Declare the value with an eagerly-known [target]. */
    public fun value(target: Schema, vararg traits: Trait): Unit = value(lazyOf(target), *traits)

    /** Declare the value whose [target] is resolved lazily (for recursive/cyclic shapes). */
    public fun value(target: Lazy<Schema>, vararg traits: Trait) {
        val memberTraits = traits.toList()
        value = {
            MemberSchemaImpl(
                shapeId = shapeId.withMember("value"),
                traits = memberTraits,
                targetProvider = { target.value },
            )
        }
    }

    internal fun build(): MapSchema {
        val k = requireNotNull(key) { "map schema $shapeId is missing its key" }
        val v = requireNotNull(value) { "map schema $shapeId is missing its value" }
        return MapSchemaImpl(shapeId, traits.toList(), k, v)
    }
}

public fun MapSchema(id: ShapeId, block: MapSchemaBuilder.() -> Unit): MapSchema = MapSchemaBuilder(id).apply(block).build()
