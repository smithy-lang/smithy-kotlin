/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

// Concrete schema implementations. Each extends AbstractSchema (for the array-indexed trait storage and
// the lazy extension cache) and implements one sealed Schema subtype. Construction goes through the
// builder DSL in SchemaBuilders.kt, never these classes directly.

internal class SimpleSchemaImpl(
    override val shapeId: ShapeId,
    override val type: ShapeType,
    traits: Lazy<Collection<Trait>>,
) : AbstractSchema(traits), SimpleSchema {
    init {
        require(type.isSimple) { "$type is not a simple shape type" }
    }
    override fun toString(): String = "SimpleSchema($shapeId: $type)"
}

internal class ListSchemaImpl(
    override val shapeId: ShapeId,
    traits: Lazy<Collection<Trait>>,
    elementProvider: () -> MemberSchema,
) : AbstractSchema(traits), ListSchema {
    override val type: ShapeType = ShapeType.LIST
    override val element: MemberSchema by lazy(elementProvider)
    override fun toString(): String = "ListSchema($shapeId)"
}

internal class MapSchemaImpl(
    override val shapeId: ShapeId,
    traits: Lazy<Collection<Trait>>,
    keyProvider: () -> MemberSchema,
    valueProvider: () -> MemberSchema,
) : AbstractSchema(traits), MapSchema {
    override val type: ShapeType = ShapeType.MAP
    override val key: MemberSchema by lazy(keyProvider)
    override val value: MemberSchema by lazy(valueProvider)
    override fun toString(): String = "MapSchema($shapeId)"
}

internal class MemberSchemaImpl(
    override val shapeId: ShapeId,
    override val memberIndex: Int,
    traits: Lazy<Collection<Trait>>,
    targetProvider: () -> Schema,
) : AbstractSchema(traits), MemberSchema {
    override val type: ShapeType = ShapeType.MEMBER
    override val memberName: String =
        requireNotNull(shapeId.member) { "member shape id $shapeId is missing a member name" }
    override val target: Schema by lazy(targetProvider)
    override fun toString(): String = "MemberSchema($shapeId -> ${target.shapeId})"
}

internal class StructureSchemaImpl(
    override val shapeId: ShapeId,
    traits: Lazy<Collection<Trait>>,
    membersProvider: () -> List<MemberSchema>,
) : AbstractSchema(traits), StructureSchema {
    override val type: ShapeType = ShapeType.STRUCTURE
    override val members: List<MemberSchema> by lazy(membersProvider)
    private val byName: Map<String, MemberSchema> by lazy { members.associateBy { it.memberName } }

    override fun member(name: String): MemberSchema? = byName[name]
    override fun member(index: Int): MemberSchema? = members.getOrNull(index)
    override fun toString(): String = "StructureSchema($shapeId)"
}

internal class UnionSchemaImpl(
    override val shapeId: ShapeId,
    traits: Lazy<Collection<Trait>>,
    membersProvider: () -> List<MemberSchema>,
) : AbstractSchema(traits), UnionSchema {
    override val type: ShapeType = ShapeType.UNION
    override val members: List<MemberSchema> by lazy(membersProvider)
    private val byName: Map<String, MemberSchema> by lazy { members.associateBy { it.memberName } }

    override fun member(name: String): MemberSchema? = byName[name]
    override fun member(index: Int): MemberSchema? = members.getOrNull(index)
    override fun toString(): String = "UnionSchema($shapeId)"
}
