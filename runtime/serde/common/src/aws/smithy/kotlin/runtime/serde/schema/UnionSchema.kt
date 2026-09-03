/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.Trait

public sealed interface UnionSchema : Schema {
    public val members: List<MemberSchema>
    public fun member(name: String): MemberSchema?
}

internal class UnionSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: Collection<Trait>,
    override val members: List<MemberSchema>,
) : UnionSchema {
    override val type: ShapeType = ShapeType.UNION
    private val byName: Map<String, MemberSchema> by lazy { members.associateBy { it.memberName } }

    override fun member(name: String): MemberSchema? = byName[name]
    override fun toString(): String = "UnionSchema($shapeId)"
}

@SchemaDsl
public class UnionSchemaBuilder internal constructor(private val shapeId: ShapeId) {
    private val traits = mutableListOf<Trait>()
    private val members = mutableListOf<MemberSchema>()
    private val memberNames = mutableSetOf<String>()

    /** Add a root-level trait. */
    public fun trait(trait: Trait) {
        traits.add(trait)
    }

    /**
     * Declare [member], which must be identified by one of this union's `$member` shape ids.
     */
    public fun member(member: MemberSchema) {
        require(member.shapeId.namespace == shapeId.namespace && member.shapeId.name == shapeId.name) {
            "union $shapeId cannot contain ${member.shapeId}, which belongs to another shape"
        }
        // Smithy requires shape ids -- and so member names -- to be unique case-insensitively
        require(memberNames.add(member.memberName.lowercase())) {
            "union $shapeId already has a member named '${member.memberName}' (case-insensitively)"
        }
        members += member
    }

    internal fun build(): UnionSchema = UnionSchemaImpl(shapeId, traits.toList(), members.toList())
}

public fun UnionSchema(id: ShapeId, block: UnionSchemaBuilder.() -> Unit): UnionSchema = UnionSchemaBuilder(id).apply(block).build()
