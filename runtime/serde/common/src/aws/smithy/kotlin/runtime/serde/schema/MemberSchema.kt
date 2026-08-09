/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.Trait

public sealed interface MemberSchema : Schema {
    override val shapeId: MemberShapeId

    /** The member's modeled name (never a wire name like `jsonName`). */
    public val memberName: String

    /** The shape this member points at. */
    public val target: Schema
}

internal class MemberSchemaImpl(
    override val shapeId: MemberShapeId,
    override val traits: Collection<Trait>,
    targetProvider: () -> Schema,
) : MemberSchema {
    override val type: ShapeType = ShapeType.MEMBER
    override val memberName: String = shapeId.member

    // target stays lazy so a recursive/self-referential shape is resolved only after construction
    override val target: Schema by lazy(targetProvider)
    override fun toString(): String = "MemberSchema($shapeId -> ${target.shapeId})"
}
