/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder

public interface UnionMemberAccess<T, V : T, M> {
    public val schema: MemberSchema<M>
    public val asVariantOrNull: (T) -> V?
    public val factory: (M) -> V
    public val valueGetter: (V) -> M
}

private data class UnionMemberAccessImpl<T, V : T, M>(
    override val schema: MemberSchema<M>,
    override val asVariantOrNull: (T) -> V?,
    override val factory: (M) -> V,
    override val valueGetter: (V) -> M,
) : UnionMemberAccess<T, V, M>

public fun <T, V : T, M> UnionMemberAccess(
    schema: MemberSchema<M>,
    asVariantOrNull: (T) -> V?,
    factory: (M) -> V,
    valueGetter: (V) -> M,
): UnionMemberAccess<T, V, M> = UnionMemberAccessImpl(schema, asVariantOrNull, factory, valueGetter)

public interface UnionSchema<T> : SerializableSchema<T> {
    public val memberAccess: List<UnionMemberAccess<T, *, *>>
}

private data class UnionSchemaImpl<T>(
    override val shapeId: ShapeId,
    override val memberAccess: List<UnionMemberAccess<T, *, *>>,
    override val traits: List<Trait>,
) : UnionSchema<T> {
    override val shapeType: ShapeType = ShapeType.UNION

    override fun deserialize(decoder: Decoder): T {
        var unionValue: T? = null
        decoder.decodeStructure { memberName, decoder ->
            fun <V : T, M> deserializeMember(member: UnionMemberAccess<T, V, M>) {
                val memberValue = member.schema.deserialize(decoder)
                unionValue = member.factory(memberValue)
            }

            val member = memberAccess.single { it.schema.shapeId.memberName == memberName } // FIXME handle SdkUnknown
            deserializeMember(member)
        }
        return checkNotNull(unionValue) { "No union members decoded!" }
    }

    override fun serialize(encoder: Encoder, value: T) {
        fun <V : T, M> serializeMember(member: UnionMemberAccess<T, V, M>): (() -> Unit)? = member
            .asVariantOrNull(value)
            ?.let { unionValue ->
                {
                    val key = member.schema.shapeId.memberName
                    val memberValue = member.valueGetter(unionValue)
                    encoder.encodeStructure { entryEncoder ->
                        entryEncoder.encodeEntry(
                            { keyEncoder -> keyEncoder.encodeString(key) },
                            { valueEncoder -> member.schema.serialize(valueEncoder, memberValue) },
                        )
                    }
                }
            }

        val serializer = memberAccess.mapNotNull { serializeMember(it) }.single() // Ensure only one variant matches
        serializer()
    }
}

public fun <T> UnionSchema(
    shapeId: ShapeId,
    memberAccess: List<UnionMemberAccess<T, *, *>>,
    traits: List<Trait>,
): UnionSchema<T> = UnionSchemaImpl(shapeId, memberAccess, traits)
