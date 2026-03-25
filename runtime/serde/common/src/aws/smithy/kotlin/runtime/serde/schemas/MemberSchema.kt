/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.serde.MemberShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder

public interface MemberSchema<T> : SerializableSchema<T> {
    override val shapeId: MemberShapeId
    public val target: SerializableSchema<T>
}

private abstract class MemberSchemaBase<T> : MemberSchema<T> {
    override val shapeType: ShapeType = ShapeType.MEMBER

    override fun deserialize(decoder: Decoder): T = target.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: T) = target.serialize(encoder, value)
}

private data class MemberSchemaImpl<T>(
    override val shapeId: MemberShapeId,
    override val target: SerializableSchema<T>,
    override val traits: List<Trait>,
) : MemberSchemaBase<T>()

public fun <T> MemberSchema(
    shapeId: MemberShapeId,
    target: SerializableSchema<T>,
    traits: List<Trait>,
): MemberSchema<T> = MemberSchemaImpl(shapeId, target, traits)

private data class LazyMemberSchemaImpl<T>(
    override val shapeId: MemberShapeId,
    private val lazyTarget: Lazy<SerializableSchema<T>>,
    override val traits: List<Trait>,
) : MemberSchemaBase<T>() {
    override val target: SerializableSchema<T> by lazyTarget
}

public fun <T> MemberSchema(
    shapeId: MemberShapeId,
    target: Lazy<SerializableSchema<T>>,
    traits: List<Trait>,
): MemberSchema<T> = LazyMemberSchemaImpl(shapeId, target, traits)
