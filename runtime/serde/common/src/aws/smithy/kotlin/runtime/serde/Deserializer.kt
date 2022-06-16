/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.smithy.Document

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
interface Deserializer {
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
    fun deserializeStruct(descriptor: SdkObjectDescriptor): FieldIterator

    /**
     * Begin deserialization of a list type. Use the returned [ElementIterator] to drive
     * the deserialization process of the list to completion.
     *
     * NOTE: An [ElementIterator] MUST be driven to completion by calling [ElementIterator.hasNextElement] until
     * `false` is returned. All elements must be consumed by deserializing appropriately.
     *
     * @param descriptor SdkFieldDescriptor the structure descriptor
     */
    fun deserializeList(descriptor: SdkFieldDescriptor): ElementIterator

    /**
     * Begin deserialization of a map type. Use the returned [EntryIterator] to drive
     * the deserialization process of the map to completion.
     *
     * NOTE: An [EntryIterator] MUST be driven to completion by calling [EntryIterator.hasNextEntry] until
     * `false` is returned. All entries must be consumed by deserializing appropriately.
     *
     * @param descriptor SdkFieldDescriptor the structure descriptor
     */
    fun deserializeMap(descriptor: SdkFieldDescriptor): EntryIterator

    /**
     * Iterator over raw elements in a collection
     */
    interface ElementIterator : PrimitiveDeserializer {
        /**
         * Advance to the next element. Returns false when no more elements are in the list
         * or the document has been read completely.
         */
        fun hasNextElement(): Boolean

        /**
         * Returns true if the next token contains a value, or false otherwise.
         */
        fun nextHasValue(): Boolean
    }

    /**
     * Iterator over map entries
     */
    interface EntryIterator : PrimitiveDeserializer {
        /**
         * Advance to the next element. Returns false when no more elements are in the map
         * or the document has been read completely.
         */
        fun hasNextEntry(): Boolean

        /**
         * Read the next key
         */
        fun key(): String

        /**
         * Returns true if the next token contains a value, or false otherwise.
         */
        fun nextHasValue(): Boolean
    }

    /**
     * Iterator over struct fields
     */
    interface FieldIterator : PrimitiveDeserializer {
        /**
         * Returns the index of the next field found, null if fields exhausted, or [UNKNOWN_FIELD].
         */
        fun findNextFieldIndex(): Int?

        /**
         * Skip the next field value recursively. Meant for discarding unknown fields
         */
        fun skipValue()

        companion object {
            /**
             * An unknown field was encountered
             */
            const val UNKNOWN_FIELD = -1
        }
    }
}

/**
 * Common interface for deserializing primitive values
 */
interface PrimitiveDeserializer {
    /**
     * Deserialize and return the next token as a [Byte]
     */
    fun deserializeByte(): Byte

    /**
     * Deserialize and return the next token as an [Int]
     */
    fun deserializeInt(): Int

    /**
     * Deserialize and return the next token as a [Short]
     */
    fun deserializeShort(): Short

    /**
     * Deserialize and return the next token as a [Long]
     */
    fun deserializeLong(): Long

    /**
     * Deserialize and return the next token as a [Float]
     */
    fun deserializeFloat(): Float

    /**
     * Deserialize and return the next token as a [Double]
     */
    fun deserializeDouble(): Double

    /**
     * Deserialize and return the next token as a [String]
     */
    fun deserializeString(): String

    /**
     * Deserialize and return the next token as a [Boolean]
     */
    fun deserializeBoolean(): Boolean

    /**
     * Deserialize and return the next token as a [Document].
     *
     * If the document's value is a list or map, this method will deserialize all elements or fields recursively - the
     * caller need not further inspect the value to attempt to do so manually.
     */
    fun deserializeDocument(): Document?

    /**
     * Consume the next token if represents a null value. Always returns null.
     */
    fun deserializeNull(): Nothing?
}

inline fun Deserializer.deserializeStruct(descriptor: SdkObjectDescriptor, block: Deserializer.FieldIterator.() -> Unit) {
    val deserializer = deserializeStruct(descriptor)
    block(deserializer)
}

inline fun <T> Deserializer.deserializeList(descriptor: SdkFieldDescriptor, block: Deserializer.ElementIterator.() -> T): T {
    val deserializer = deserializeList(descriptor)
    return block(deserializer)
}

inline fun <T> Deserializer.deserializeMap(descriptor: SdkFieldDescriptor, block: Deserializer.EntryIterator.() -> T): T {
    val deserializer = deserializeMap(descriptor)
    return block(deserializer)
}
