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

public interface ShapeDeserializer {
    // ── simple types ──────────────────────────────────────────────────────────────────────────────
    public fun readBoolean(schema: Schema): Boolean
    public fun readByte(schema: Schema): Byte
    public fun readShort(schema: Schema): Short
    public fun readInt(schema: Schema): Int
    public fun readLong(schema: Schema): Long
    public fun readFloat(schema: Schema): Float
    public fun readDouble(schema: Schema): Double
    public fun readBigInteger(schema: Schema): BigInteger
    public fun readBigDecimal(schema: Schema): BigDecimal
    public fun readString(schema: Schema): String
    public fun readBlob(schema: Schema): ByteArray
    public fun readTimestamp(schema: Schema): Instant
    public fun readDocument(schema: Schema): Document?

    /** True if the next value is null (used when reading sparse lists and maps). */
    public fun isNull(): Boolean

    // ── aggregate types ───────────────────────────────────────────────────────────────────────────
    public fun <T> readStruct(schema: Schema, state: T, consumer: StructConsumer<T>)
    public fun <T> readList(schema: Schema, state: T, consumer: ListConsumer<T>)
    public fun <T> readMap(schema: Schema, state: T, consumer: MapConsumer<T>)
}

/** Invoked once per structure/union member found; [state] is threaded explicitly so nothing is captured. */
public fun interface StructConsumer<T> {
    public fun accept(state: T, member: MemberSchema, deserializer: ShapeDeserializer)
}

/** Invoked once per list element. */
public fun interface ListConsumer<T> {
    public fun accept(state: T, deserializer: ShapeDeserializer)
}

/** Invoked once per map entry, with the entry's [key]. */
public fun interface MapConsumer<T> {
    public fun accept(state: T, key: String, deserializer: ShapeDeserializer)
}
