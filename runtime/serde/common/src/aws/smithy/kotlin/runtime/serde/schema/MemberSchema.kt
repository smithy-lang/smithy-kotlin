/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.Trait

public sealed interface MemberSchema : Schema {
    override val shapeId: MemberShapeId

    /** The member's modeled name. */
    public val memberName: String

    /** The shape this member points at. */
    public val target: Schema
}

internal class MemberSchemaImpl(
    override val shapeId: MemberShapeId,
    override val traits: Collection<Trait>,
    private val lazyTarget: Lazy<Schema>,
) : MemberSchema {
    constructor(shapeId: MemberShapeId, traits: Collection<Trait>, target: Schema) :
        this(shapeId, traits, lazyOf(target))

    override val type: ShapeType = ShapeType.MEMBER
    override val memberName: String = shapeId.member

    override val target: Schema get() = lazyTarget.value
    override fun toString(): String = "MemberSchema($shapeId)"
}

/** Create a [MemberSchema] identified by [id] that targets [target]. */
public fun MemberSchema(id: MemberShapeId, target: Schema, vararg traits: Trait): MemberSchema = MemberSchemaImpl(id, traits.toList(), target)

/**
 * Create a [MemberSchema] identified by [id] whose [target] is resolved on first access, for recursive or cyclic
 * shapes.
 */
public fun MemberSchema(id: MemberShapeId, target: Lazy<Schema>, vararg traits: Trait): MemberSchema = MemberSchemaImpl(id, traits.toList(), target)
