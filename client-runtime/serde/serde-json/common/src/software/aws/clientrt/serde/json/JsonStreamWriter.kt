/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.json

/**
 * Interface for serialization. Specific formats should implement this interface according to their
 * own requirements. Currently only software.aws.clientrt.serde.json.JsonSerializer implements this interface.
 */
interface JsonStreamWriter {

    /**
     * Begins encoding a new array. Each call to this method must be paired with
     * a call to {@link #endArray}.
     */
    fun beginArray()

    /**
     * Ends encoding the current array.
     */
    fun endArray()

    /**
     * Encodes {@code null}.
     */
    fun writeNull()

    /**
     * Begins encoding a new object. Each call to this method must be paired
     * with a call to {@link #endObject}.
     */
    fun beginObject()

    /**
     * Ends encoding the current object.
     */
    fun endObject()

    /**
     * Encodes the property name.
     *
     * @param name the name of the forthcoming value. May not be null.
     */
    fun writeName(name: String)

    /**
     * Encodes {@code value}.
     *
     * @param value the literal string value, or null to encode a null literal.
     */
    fun writeValue(value: String)

    /**
     * Encodes {@code value}.
     */
    fun writeValue(bool: Boolean)

    /**
     * Encodes {@code value}.
     */
    fun writeValue(value: Long)

    /**
     * Encodes {@code value}.
     *
     * @param value a finite value. May not be {@link Double#isNaN() NaNs} or
     *     {@link Double#isInfinite() infinities}.
     */
    fun writeValue(value: Double)

    /**
     * Encodes {@code value}.
     */
    fun writeValue(value: Float)

    /**
     * Encodes {@code value}.
     */
    fun writeValue(value: Short)

    /**
     * Encodes {@code value}.
     */
    fun writeValue(value: Int)

    /**
     * Encodes {@code value}.
     */
    fun writeValue(value: Byte)

    /**
     * Appends the contents of [value] *without* any additional formatting or escaping. Use with caution
     */
    fun writeRawValue(value: String)

    /**
     * Json content will be constructed in this UTF-8 encoded byte array.
     */
    val bytes: ByteArray?
}

/*
* Creates a [JsonStreamWriter] instance to write JSON
*/
internal expect fun jsonStreamWriter(pretty: Boolean = false): JsonStreamWriter
