/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.serde

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
 * loop@while(true) {
 *     when(struct.nextField(OBJ_DESCRIPTOR)) {
 *         X_DESCRIPTOR.index ->  x = struct.deserializeInt()
 *         Y_DESCRIPTOR.index -> y = struct.deserializeInt()
 *         Deserializer.FieldIterator.EXHAUSTED -> break@loop
 *         else -> struct.skipValue()
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
interface Deserializer : PrimitiveDeserializer {
    /**
     * Begin deserialization of a structured type. Use the returned [FieldIterator] to drive
     * the deserialization process of the struct to completion.
     */
    fun deserializeStruct(descriptor: SdkFieldDescriptor?): FieldIterator

    /**
     * Begin deserialization of a list type. Use the returned [ElementIterator] to drive
     * the deserialization process of the list to completion.
     */
    fun deserializeList(): ElementIterator

    /**
     * Begin deserialization of a map type. Use the returned [EntryIterator] to drive
     * the deserialization process of the map to completion.
     */
    fun deserializeMap(): EntryIterator

    /**
     * Iterator over raw elements in a collection
     */
    interface ElementIterator : PrimitiveDeserializer {
        /**
         * Advance to the next element. Returns [EXHAUSTED] when no more elements are in the list
         * or the document has been read completely.
         */
        fun next(): Int

        companion object {
            /**
             * The iterator has been exhausted, no more fields will be returned by [next]
             */
            const val EXHAUSTED = -1
        }
    }

    /**
     * Iterator over map entries
     */
    interface EntryIterator : PrimitiveDeserializer {
        /**
         * Advance to the next element. Returns [EXHAUSTED] when no more elements are in the map
         * or the document has been read completely.
         */
        fun next(): Int

        /**
         * Read the next key
         */
        fun key(): String

        companion object {
            /**
             * The iterator has been exhausted, no more fields will be returned by [next]
             */
            const val EXHAUSTED = -1
        }
    }

    /**
     * Iterator over struct fields
     */
    interface FieldIterator : PrimitiveDeserializer {
        /**
         * Returns the index of the next field found or one of the defined constants
         */
        fun nextField(descriptor: SdkObjectDescriptor): Int

        /**
         * Skip the next field value recursively. Meant for discarding unknown fields
         */
        fun skipValue()

        companion object {
            /**
             * The iterator has been exhausted, no more fields will be returned by [nextField]
             */
            const val EXHAUSTED = -1

            /**
             * An unknown field was encountered
             */
            const val UNKNOWN_FIELD = -2
        }
    }
}

fun Deserializer.deserializeStruct(descriptor: SdkFieldDescriptor?, block: Deserializer.FieldIterator.() -> Unit) {
    val iter = deserializeStruct(descriptor)
    iter.apply(block)
}

fun <T> Deserializer.deserializeList(block: Deserializer.ElementIterator.() -> T): T {
    val deserializer = deserializeList()
    val result = block(deserializer)
    return result
}

fun <T> Deserializer.deserializeMap(block: Deserializer.EntryIterator.() -> T): T {
    val deserializer = deserializeMap()
    val result = block(deserializer)
    return result
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
    fun deserializeBool(): Boolean
}
