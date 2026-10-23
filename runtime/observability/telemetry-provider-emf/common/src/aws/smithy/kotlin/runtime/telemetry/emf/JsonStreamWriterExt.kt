/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.serde.json.JsonStreamWriter

/**
 * Writes an anonymous JSON object, invoking [block] to write its contents.
 */
internal inline fun JsonStreamWriter.withObject(block: JsonStreamWriter.() -> Unit) {
    beginObject()
    block()
    endObject()
}

/**
 * Writes a JSON object named [key], invoking [block] to write its contents.
 */
internal inline fun JsonStreamWriter.withObject(key: String, block: JsonStreamWriter.() -> Unit) {
    writeName(key)
    withObject(block)
}

/**
 * Writes an anonymous JSON array, invoking [block] to write its elements.
 */
internal inline fun JsonStreamWriter.withArray(block: JsonStreamWriter.() -> Unit) {
    beginArray()
    block()
    endArray()
}

/**
 * Writes a JSON array named [key], invoking [block] to write its elements.
 */
internal inline fun JsonStreamWriter.withArray(key: String, block: JsonStreamWriter.() -> Unit) {
    writeName(key)
    withArray(block)
}

/**
 * Writes a `"key": value` pair with a string [value].
 */
internal fun JsonStreamWriter.writeEntry(key: String, value: String) {
    writeName(key)
    writeValue(value)
}

/**
 * Writes a `"key": value` pair with a numeric [value].
 */
internal fun JsonStreamWriter.writeEntry(key: String, value: Number) {
    writeName(key)
    writeValue(value)
}

/**
 * Writes a `"key": value` pair with a string [value], or nothing at all if [value] is null.
 */
internal fun JsonStreamWriter.writeEntryIfNotNull(key: String, value: String?) {
    if (value != null) writeEntry(key, value)
}
