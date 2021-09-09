/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.json

import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class JsonStreamWriterTest {
    @Test
    fun testArrayOfObjects() {
        // language=JSON
        val expected = """[
    {
        "id": 912345678901,
        "text": "How do I stream JSON in Java?",
        "geo": null,
        "user": {
            "name": "json_newb",
            "followers_count": 41
        }
    },
    {
        "id": 912345678902,
        "text": "@json_newb just use JsonWriter!",
        "geo": [
            50.454722,
            -104.606667
        ],
        "user": {
            "name": "jesse",
            "followers_count": 2
        }
    }
]"""

        val messages = listOf(
            Message(
                912345678901,
                "How do I stream JSON in Java?",
                null,
                User("json_newb", 41)
            ),
            Message(
                912345678902,
                "@json_newb just use JsonWriter!",
                listOf(50.454722, -104.606667),
                User("jesse", 2)
            )
        )

        val writer = jsonStreamWriter(true)
        val actual = requireNotNull(writeMessagesArray(writer, messages)).decodeToString()

        assertEquals(expected, actual)
    }

    @Test
    fun testObject() {
        val writer = jsonStreamWriter()
        writer.beginObject()
        writer.writeName("id")
        writer.writeValue(912345678901)
        writer.endObject()
        // language=JSON
        val expected = """{"id":912345678901}"""
        assertEquals(expected, writer.bytes?.decodeToString())
    }

    @Test
    fun testWriteRawValue() {
        val writer = jsonStreamWriter()
        // language=JSON
        val expected = """{"foo":1234.5678}"""
        writer.writeRawValue(expected)
        assertEquals(expected, writer.bytes?.decodeToString())
    }

    @Test
    fun testNested() {
        // language=JSON
        val expected = """{
    "foo": "bar",
    "nested": {
        "array": [
            1,
            2,
            3
        ],
        "bool": true
    },
    "baz": -1.23
}"""
        val writer = jsonStreamWriter(true).apply {
            beginObject()
            writeName("foo")
            writeValue("bar")
            writeName("nested")
            beginObject()
            writeName("array")
            beginArray()
            writeValue(1)
            writeValue(2)
            writeValue(3)
            endArray()
            writeName("bool")
            writeValue(true)
            endObject()
            writeName("baz")
            writeValue(-1.23)
            endObject()
        }
        val actual = writer.bytes?.decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun testBoolean() {
        val actual = jsonStreamWriter().apply {
            beginArray()
            writeValue(true)
            writeValue(false)
            endArray()
        }.bytes?.decodeToString()
        assertEquals("[true,false]", actual)
    }

    @Test
    fun testNull() {
        val actual = jsonStreamWriter().apply {
            writeNull()
        }.bytes?.decodeToString()
        assertEquals("null", actual)
    }

    @Test
    fun testEmpty() {
        val actualEmptyArray = jsonStreamWriter().apply {
            beginArray()
            endArray()
        }.bytes?.decodeToString()
        assertEquals("[]", actualEmptyArray)

        val actualEmptyObject = jsonStreamWriter().apply {
            beginObject()
            endObject()
        }.bytes?.decodeToString()
        assertEquals("{}", actualEmptyObject)
    }

    @Test
    fun testObjectInsideArray() {
        val actual = jsonStreamWriter().apply {
            beginArray()
            repeat(3) {
                beginObject()
                endObject()
            }
            endArray()
        }.bytes?.decodeToString()
        assertEquals("[{},{},{}]", actual)
    }

    @Test
    fun testObjectInsideObject() {
        val actual = jsonStreamWriter().apply {
            beginObject()
            writeName("nested")
            beginObject()
            writeName("foo")
            writeValue("bar")
            endObject()
            endObject()
        }.bytes?.decodeToString()
        assertEquals("""{"nested":{"foo":"bar"}}""", actual)
    }

    @Test
    fun testArrayInsideObject() {
        val actual = jsonStreamWriter().apply {
            beginObject()
            writeName("foo")
            beginArray()
            endArray()

            writeName("b\nar")
            beginArray()
            endArray()
            endObject()
        }.bytes?.decodeToString()
        assertEquals("""{"foo":[],"b\nar":[]}""", actual)
    }

    @Test
    fun testArrayInsideArray() {
        val actual = jsonStreamWriter().apply {
            beginArray()
            beginArray()
            writeValue(5)
            endArray()
            beginArray()
            endArray()
            endArray()
        }.bytes?.decodeToString()
        assertEquals("""[[5],[]]""", actual)
    }

    @Test
    fun testEscape() {
        val tests = listOf(
            // sanity check values that shouldn't be escaped
            "" to "",
            "foo" to "foo",
            // surrogate pair
            "\uD801\uDC37" to "\uD801\uDC37",

            // escaped
            "foo\r\n" to "foo\\r\\n",
            "foo\r\nbar" to "foo\\r\\nbar",
            "foo\bar" to "foo\\bar",
            "\u000Coobar" to "\\foobar",
            "\u0008f\u000Co\to\r\n" to "\\bf\\fo\\to\\r\\n",
            "\"test\"" to "\\\"test\\\"",
            "\u0000" to "\\u0000",
            "\u001f" to "\\u001f",
        )

        tests.forEachIndexed { idx, test ->
            assertEquals(test.second, test.first.escape(), "[idx=$idx] escaped value not equal")
        }
    }
}

private fun writeMessagesArray(writer: JsonStreamWriter, messages: List<Message>): ByteArray? {
    writer.beginArray()
    for (message in messages) {
        writeMessage(writer, message)
    }
    writer.endArray()
    return writer.bytes
}

private fun writeMessage(writer: JsonStreamWriter, message: Message) {
    writer.beginObject()
    writer.writeName("id")
    writer.writeValue(message.id)
    writer.writeName("text")
    writer.writeValue(message.text)
    if (message.geo != null) {
        writer.writeName("geo")
        writeDoublesArray(writer, message.geo)
    } else {
        writer.writeName("geo")
        writer.writeNull()
    }
    writer.writeName("user")
    writeUser(writer, message.user)
    writer.endObject()
}

private fun writeUser(writer: JsonStreamWriter, user: User) {
    writer.beginObject()
    writer.writeName("name")
    writer.writeValue(user.name)
    writer.writeName("followers_count")
    writer.writeValue(user.followersCount)
    writer.endObject()
}

private fun writeDoublesArray(writer: JsonStreamWriter, doubles: List<Double>?) {
    writer.beginArray()
    if (doubles != null) {
        for (value in doubles) {
            writer.writeValue(value)
        }
    }
    writer.endArray()
}

private data class Message(val id: Long, val text: String, val geo: List<Double>?, val user: User)
private data class User(val name: String, val followersCount: Int)
