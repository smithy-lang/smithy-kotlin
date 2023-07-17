/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.content.BigDecimal
import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.content.Document

/**
 * Deserializer is a format agnostic deserialization interface. Specific formats (e.g. JSON, XML, etc) implement
 * this interface and handle the underlying raw decoding process and deal with details specific to that format.
 *
 * This allows the same deserialization process to work between formats which is useful for code generation.
 *
 * ### Deserializing Structured Types
 *
 * A Kotlin class is represented as a structure with fields. The order the fields present themselves may not
 * be guaranteed or consistent in some formats (e.g. JSON and XML). This requires deserialization to iterate
 * over the fields found in the underlying stream and the deserializer will tell you which field was encountered.
 * This is done by giving the serializer an [SdkObjectDescriptor] which describes the fields expected.
 *
 * ```
 * data class Point(val x: Int, val y: Int)
 *
 * val struct = deserializer.deserializeStruct()
 * var x: Int? = null
 * var y: Int? = null
 *
 * val X_DESCRIPTOR = SdkFieldDescriptor("x")
 * val Y_DESCRIPTOR = SdkFieldDescriptor("y")
 * val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
 *     field(X_DESCRIPTOR)
 *     field(Y_DESCRIPTOR)
 * }
 * loop@ while(true) {
 *     when(struct.findNextFieldIndexOrNull()) {
 *         X_DESCRIPTOR.index ->  x = struct.deserializeInt()
 *         Y_DESCRIPTOR.index -> y = struct.deserializeInt()
 *         null -> break@loop
 *         else -> struct.skipValue() // Unknown Field
 *     }
 * }
 * requireNotNull(x)
 * requireNotNull(y)
 * val myPoint = Point(x!!, y!!)
 * ```
 *
 *
 * ### Deserializing Collections
 *
 * Collections such as List and Map work almost the same as deserializing a structured type except iteration
 * is over elements (or entries) instead of fields. Deserialization implementations should drive the iterator
 * until it is exhausted and for each element/entry call the appropriate `deserialize*` methods.
 *
 */
@InternalApi
public interface Deserializer {
    /**
     * Begin deserialization of a structured type. Use the returned [FieldIterator] to drive
     * the deserialization process of the struct to completion.
     *
     * NOTE: A [FieldIterator] MUST be driven to completion by calling [FieldIterator.findNextFieldIndex] until
     * `null` is returned. All field values must be consumed either by deserializing appropriately or skipping
     * the field with [FieldIterator.skipValue].
     *
     * @param descriptor SdkObjectDescriptor the structure descriptor
     */
    public fun deserializeStruct(descriptor: SdkObjectDescriptor): FieldIterator

    /**
     * Begin deserialization of a list type. Use the returned [ElementIterator] to drive
     * the deserialization process of the list to completion.
     *
     * NOTE: An [ElementIterator] MUST be driven to completion by calling [ElementIterator.hasNextElement] until
     * `false` is returned. All elements must be consumed by deserializing appropriately.
     *
     * @param descriptor SdkFieldDescriptor the structure descriptor
     */
    public fun deserializeList(descriptor: SdkFieldDescriptor): ElementIterator

    /**
     * Begin deserialization of a map type. Use the returned [EntryIterator] to drive
     * the deserialization process of the map to completion.
     *
     * NOTE: An [EntryIterator] MUST be driven to completion by calling [EntryIterator.hasNextEntry] until
     * `false` is returned. All entries must be consumed by deserializing appropriately.
     *
     * @param descriptor SdkFieldDescriptor the structure descriptor
     */
    public fun deserializeMap(descriptor: SdkFieldDescriptor): EntryIterator

    /**
     * Iterator over raw elements in a collection
     */
    @InternalApi
    public interface ElementIterator : PrimitiveDeserializer {
        /**
         * Advance to the next element. Returns false when no more elements are in the list
         * or the document has been read completely.
         */
        public fun hasNextElement(): Boolean

        /**
         * Returns true if the next token contains a value, or false otherwise.
         */
        public fun nextHasValue(): Boolean
    }

    /**
     * Iterator over map entries
     */
    @InternalApi
    public interface EntryIterator : PrimitiveDeserializer {
        /**
         * Advance to the next element. Returns false when no more elements are in the map
         * or the document has been read completely.
         */
        public fun hasNextEntry(): Boolean

        /**
         * Read the next key
         */
        public fun key(): String

        /**
         * Returns true if the next token contains a value, or false otherwise.
         */
        public fun nextHasValue(): Boolean
    }

    /**
     * Iterator over struct fields
     */
    @InternalApi
    public interface FieldIterator : PrimitiveDeserializer {
        /**
         * Returns the index of the next field found, null if fields exhausted, or [UNKNOWN_FIELD].
         */
        public fun findNextFieldIndex(): Int?

        /**
         * Skip the next field value recursively. Meant for discarding unknown fields
         */
        public fun skipValue()

        @InternalApi
        public companion object {
            /**
             * An unknown field was encountered
             */
            public const val UNKNOWN_FIELD: Int = -1
        }
    }
}

/**
 * Common interface for deserializing primitive values
 */
@InternalApi
public interface PrimitiveDeserializer {
    /**
     * Deserialize and return the next token as a [Byte]
     */
    public fun deserializeByte(): Byte

    /**
     * Deserialize and return the next token as an [Int]
     */
    public fun deserializeInt(): Int

    /**
     * Deserialize and return the next token as a [Short]
     */
    public fun deserializeShort(): Short

    /**
     * Deserialize and return the next token as a [Long]
     */
    public fun deserializeLong(): Long

    /**
     * Deserialize and return the next token as a [Float]
     */
    public fun deserializeFloat(): Float

    /**
     * Deserialize and return the next token as a [Double]
     */
    public fun deserializeDouble(): Double

    /**
     * Deserialize and return the next token as a [BigInteger]
     */
    public fun deserializeBigInteger(): BigInteger

    /**
     * Deserialize and return the next token as a [BigDecimal]
     */
    public fun deserializeBigDecimal(): BigDecimal

    /**
     * Deserialize and return the next token as a [String]
     */
    public fun deserializeString(): String

    /**
     * Deserialize and return the next token as a [Boolean]
     */
    public fun deserializeBoolean(): Boolean

    /**
     * Deserialize and return the next token as a [Document].
     *
     * If the document's value is a list or map, this method will deserialize all elements or fields recursively - the
     * caller need not further inspect the value to attempt to do so manually.
     */
    public fun deserializeDocument(): Document

    /**
     * Consume the next token if represents a null value. Always returns null.
     */
    public fun deserializeNull(): Nothing?
}

@InternalApi
public inline fun Deserializer.deserializeStruct(descriptor: SdkObjectDescriptor, block: Deserializer.FieldIterator.() -> Unit) {
    val deserializer = deserializeStruct(descriptor)
    block(deserializer)
}

@InternalApi
public inline fun <T> Deserializer.deserializeList(descriptor: SdkFieldDescriptor, block: Deserializer.ElementIterator.() -> T): T {
    val deserializer = deserializeList(descriptor)
    return block(deserializer)
}

@InternalApi
public inline fun <T> Deserializer.deserializeMap(descriptor: SdkFieldDescriptor, block: Deserializer.EntryIterator.() -> T): T {
    val deserializer = deserializeMap(descriptor)
    return block(deserializer)
}
