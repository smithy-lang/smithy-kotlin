/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.Trait

public sealed interface ListSchema : Schema {
    public val element: MemberSchema
}

internal class ListSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: Collection<Trait>,
    elementProvider: () -> MemberSchema,
) : ListSchema {
    override val type: ShapeType = ShapeType.LIST
    override val element: MemberSchema by lazy(elementProvider)
    override fun toString(): String = "ListSchema($shapeId)"
}

@SchemaDsl
public class ListSchemaBuilder internal constructor(private val shapeId: ShapeId) {
    private val traits = mutableListOf<Trait>()
    private var element: (() -> MemberSchema)? = null

    /** Add a root-level trait. */
    public fun trait(trait: Trait) {
        traits.add(trait)
    }

    /** Declare the element with an eagerly-known [target]. */
    public fun element(target: Schema, vararg traits: Trait): Unit = element(lazyOf(target), *traits)

    /** Declare the element whose [target] is resolved lazily (for recursive/cyclic shapes). */
    public fun element(target: Lazy<Schema>, vararg traits: Trait) {
        val memberTraits = traits.toList()
        element = {
            MemberSchemaImpl(
                shapeId = shapeId.withMember("member"),
                traits = memberTraits,
                targetProvider = { target.value },
            )
        }
    }

    internal fun build(): ListSchema {
        val e = requireNotNull(element) { "list schema $shapeId is missing its element" }
        return ListSchemaImpl(shapeId, traits.toList(), e)
    }
}

public fun ListSchema(id: ShapeId, block: ListSchemaBuilder.() -> Unit): ListSchema = ListSchemaBuilder(id).apply(block).build()
