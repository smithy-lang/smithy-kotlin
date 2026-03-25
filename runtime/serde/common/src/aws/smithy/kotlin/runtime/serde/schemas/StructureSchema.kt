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
import kotlin.reflect.KMutableProperty1

public interface StructureMemberAccess<T, B, M> {
    public val schema: MemberSchema<M>
    public val getter: (T) -> M
    public val setter: B.(M) -> Unit
}

private data class StructureMemberAccessImpl<T, B, M>(
    override val schema: MemberSchema<M>,
    override val getter: (T) -> M,
    override val setter: B.(M) -> Unit,
) : StructureMemberAccess<T, B, M>

@Suppress("ktlint:standard:function-naming")
public fun <T, B, M> StructureMemberAccess(
    schema: MemberSchema<M>,
    getter: (T) -> M,
    setter: B.(M) -> Unit,
) : StructureMemberAccess<T, B, M> = StructureMemberAccessImpl(schema, getter, setter)

// Function exists solely to aid compiler type resolution
@Suppress("NOTHING_TO_INLINE")
public inline fun <B, M> property(prop: KMutableProperty1<B, M>): KMutableProperty1<B, M> = prop

public interface StructureSchema<T> : SerializableSchema<T> {
    public val members: List<MemberSchema<*>>
}

private data class StructureSchemaImpl<T, B>(
    override val shapeId: ShapeId,
    private val memberAccess: List<StructureMemberAccess<T, B, *>>,
    private val factory: (B.() -> Unit) -> T,
    override val traits: List<Trait>,
) : StructureSchema<T> {
    override val members: List<MemberSchema<*>> = memberAccess.map { it.schema }
    override val shapeType: ShapeType = ShapeType.STRUCTURE

    override fun deserialize(decoder: Decoder): T = factory {
        decoder.decodeStructure { memberName, valueDecoder ->
            fun <M> deserializeMember(member: StructureMemberAccess<T, B, M>) {
                val value = member.schema.deserialize(valueDecoder)
                member.setter(this, value)
            }

            val member = memberAccess.single { it.schema.shapeId.memberName == memberName }
            deserializeMember(member)
        }
    }

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeStructure { entryEncoder ->
        fun <M> serializeMember(member: StructureMemberAccess<T, B, M>) {
            val key = member.schema.shapeId.memberName
            val value = member.getter(value)
            entryEncoder.encodeEntry(
                { keyEncoder -> keyEncoder.encodeString(key) },
                { valueEncoder -> member.schema.serialize(valueEncoder, value) },
            )
        }

        memberAccess.forEach { member -> serializeMember(member) }
    }
}

public fun <T, B> StructureSchema(
    shapeId: ShapeId,
    memberAccess: List<StructureMemberAccess<T, B, *>>,
    factory: (B.() -> Unit) -> T,
    traits: List<Trait>,
): StructureSchema<T> = StructureSchemaImpl(shapeId, memberAccess, factory, traits)
