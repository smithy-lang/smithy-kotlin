/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.SerializationException
import com.google.gson.stream.JsonWriter
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

/**
 * Thin wrapper around Gson's JSON generator. Uses the gson.stream library's JsonWriter.
 * https://www.javadoc.io/doc/com.google.code.gson/gson/latest/com.google.gson/com/google/gson/stream/JsonWriter.html
 */
private class JsonStreamWriterGson(pretty: Boolean) : JsonStreamWriter {
    private val DEFAULT_BUFFER_SIZE = 1024
    private val baos: ByteArrayOutputStream = ByteArrayOutputStream(DEFAULT_BUFFER_SIZE)

    private var jsonStreamWriter: JsonWriter

    init {
        try {
            /**
             * A [JsonWriter] created is by default enabled with UTF-8 encoding
             */
            val bufferedWriter = BufferedWriter(OutputStreamWriter(baos, "UTF-8"))
            var jsonWriter = JsonWriter(bufferedWriter)
            if (pretty) {
                jsonWriter.setIndent("    ")
            }
            jsonStreamWriter = jsonWriter
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    /**
     * Closes the jsonStreamWriter and flushes to write. Must be called when finished writing JSON
     * content.
     */
    private fun close() {
        try {
            jsonStreamWriter.close()
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    /**
     * Get the JSON content as a UTF-8 encoded byte array. It is recommended to hold onto the array
     * reference rather then making repeated calls to this method as a new array will be created
     * each time.
     *
     * @return Array of UTF-8 encoded bytes that make up the generated JSON.
     */
    override val bytes: ByteArray
        get() {
            close()
            return baos.toByteArray()
        }

    override fun beginArray() {
        try {
            jsonStreamWriter.beginArray()
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun endArray() {
        try {
            jsonStreamWriter.endArray()
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeNull() {
        try {
            jsonStreamWriter.nullValue()
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun beginObject() {
        try {
            jsonStreamWriter.beginObject()
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun endObject() {
        try {
            jsonStreamWriter.endObject()
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeName(name: String) {
        try {
            jsonStreamWriter.name(name)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeValue(value: String) {
        try {
            jsonStreamWriter.value(value)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeValue(bool: Boolean) {
        try {
            jsonStreamWriter.value(bool)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeValue(value: Long) {
        try {
            jsonStreamWriter.value(value)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeValue(value: Double) {
        try {
            jsonStreamWriter.value(value)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeValue(value: Float) {
        try {
            jsonStreamWriter.value(value)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeValue(value: Short) {
        try {
            jsonStreamWriter.value(value)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeValue(value: Int) {
        try {
            jsonStreamWriter.value(value)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeValue(value: Byte) {
        try {
            jsonStreamWriter.value(value.toLong())
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }

    override fun writeRawValue(value: String) {
        try {
            jsonStreamWriter.jsonValue(value)
        } catch (e: IOException) {
            throw SerializationException(e)
        }
    }
}

/*
* Creates a [JsonStreamWriter] instance to write JSON
*/
internal actual fun jsonStreamWriter(pretty: Boolean): JsonStreamWriter = JsonStreamWriterGson(pretty)
