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
    private val memberNames = mutableSetOf<String>()

    /** Add a root-level trait. */
    public fun trait(trait: Trait) {
        traits.add(trait)
    }

    /**
     * Declare [member], which must be identified by one of this structure's `$member` shape ids.
     */
    public fun member(member: MemberSchema) {
        require(member.shapeId.namespace == shapeId.namespace && member.shapeId.name == shapeId.name) {
            "structure $shapeId cannot contain ${member.shapeId}, which belongs to another shape"
        }
        // Smithy requires shape ids -- and so member names -- to be unique case-insensitively
        require(memberNames.add(member.memberName.lowercase())) {
            "structure $shapeId already has a member named '${member.memberName}' (case-insensitively)"
        }
        members += member
    }

    internal fun build(): StructureSchema = StructureSchemaImpl(shapeId, traits.toList(), members.toList())
}

public fun StructureSchema(id: ShapeId, block: StructureSchemaBuilder.() -> Unit): StructureSchema = StructureSchemaBuilder(id).apply(block).build()
