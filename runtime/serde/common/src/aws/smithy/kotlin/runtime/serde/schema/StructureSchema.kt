/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.Trait

public sealed interface StructureSchema : Schema {
    public val members: List<MemberSchema>
    public fun member(name: String): MemberSchema?
}

internal class StructureSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: Collection<Trait>,
    override val members: List<MemberSchema>,
) : StructureSchema {
    override val type: ShapeType = ShapeType.STRUCTURE
    private val byName: Map<String, MemberSchema> by lazy { members.associateBy { it.memberName } }

    override fun member(name: String): MemberSchema? = byName[name]
    override fun toString(): String = "StructureSchema($shapeId)"
}

@SchemaDsl
public class StructureSchemaBuilder internal constructor(private val shapeId: ShapeId) {
    private val traits = mutableListOf<Trait>()
    private val members = mutableListOf<MemberSchema>()

    /** Add a root-level trait. */
    public fun trait(trait: Trait) {
        traits.add(trait)
    }

    /** Declare a member with an eagerly-known [target]. */
    public fun member(name: String, target: Schema, vararg traits: Trait) {
        members += MemberSchemaImpl(shapeId.withMember(name), traits.toList(), target)
    }

    /** Declare a member whose [target] is resolved lazily (for recursive/cyclic shapes). */
    public fun member(name: String, target: Lazy<Schema>, vararg traits: Trait) {
        members += MemberSchemaImpl(shapeId.withMember(name), traits.toList(), target)
    }

    internal fun build(): StructureSchema = StructureSchemaImpl(shapeId, traits.toList(), members.toList())
}

public fun StructureSchema(id: ShapeId, block: StructureSchemaBuilder.() -> Unit): StructureSchema = StructureSchemaBuilder(id).apply(block).build()
