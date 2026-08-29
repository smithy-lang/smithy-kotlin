/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema.serde

import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document
import aws.smithy.kotlin.runtime.serde.schema.MemberSchema
import aws.smithy.kotlin.runtime.serde.schema.Schema
import aws.smithy.kotlin.runtime.time.Instant

/** DSL marker preventing accidental use of an outer serializer receiver inside a nested write block. */
@DslMarker
public annotation class SerializerDsl

@SerializerDsl
public interface ValueSerializer {
    // ── simple types ──────────────────────────────────────────────────────────────────────────────
    public fun writeBoolean(schema: Schema, value: Boolean)
    public fun writeByte(schema: Schema, value: Byte)
    public fun writeShort(schema: Schema, value: Short)
    public fun writeInt(schema: Schema, value: Int)
    public fun writeLong(schema: Schema, value: Long)
    public fun writeFloat(schema: Schema, value: Float)
    public fun writeDouble(schema: Schema, value: Double)
    public fun writeBigInteger(schema: Schema, value: BigInteger)
    public fun writeBigDecimal(schema: Schema, value: BigDecimal)
    public fun writeString(schema: Schema, value: String)
    public fun writeBlob(schema: Schema, value: ByteArray)
    public fun writeTimestamp(schema: Schema, value: Instant)
    public fun writeDocument(schema: Schema, value: Document?)

    /** Writes a null; not a Smithy type but required for sparse lists and maps. */
    public fun writeNull(schema: Schema)

    // ── aggregate types ───────────────────────────────────────────────────────────────────────────
    public fun writeStruct(schema: Schema, block: StructSerializer.() -> Unit)
    public fun writeList(schema: Schema, size: Int, block: ListSerializer.() -> Unit)
    public fun writeMap(schema: Schema, size: Int, block: MapSerializer.() -> Unit)

    public fun writeStruct(schema: Schema, value: SerializableStruct): Unit = writeStruct(schema) { value.serializeMembers(this) }
}

/** Receiver for writing a structure's or union's members (each via a `writeXxx(memberSchema, …)`). */
public interface StructSerializer : ValueSerializer

/** Receiver for writing a list's elements (each via a `writeXxx(elementSchema, …)`). */
public interface ListSerializer : ValueSerializer

/** Receiver for writing a map's entries. */
@SerializerDsl
public interface MapSerializer {
    /** Writes a single entry: its [key] (described by [keySchema]) and the value written inside [block]. */
    public fun entry(keySchema: MemberSchema, key: String, block: ValueSerializer.() -> Unit)
}

/**
 * A [ValueSerializer] that accumulates into a serialization target type [F], produced by [flush].
 */
public interface ShapeSerializer<F> : ValueSerializer {
    public fun flush(): F
}
