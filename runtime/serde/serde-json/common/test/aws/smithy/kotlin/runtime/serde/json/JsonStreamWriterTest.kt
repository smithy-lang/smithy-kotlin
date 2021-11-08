/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalStdlibApi::class)
class JsonStreamWriterTest {
    @Test
    fun testArrayOfObjects() {
        // language=JSON
        val expected = """[
    {
        "id": "msg1",
        "meta": {
            "description": "first message"
        },
        "nestedArray": [
            1,
            2.2,
            -3.3333
        ],
        "integerId": 1
    },
    {
        "id": "msg2",
        "meta": {
            "description": "second message"
        },
        "nestedArray": [
            4,
            5
        ],
        "integerId": 2
    }
]"""

        val writer = jsonStreamWriter(true).apply {
            beginArray()

            beginObject()
            writeName("id")
            writeValue("msg1")
            writeName("meta")
            beginObject()
            writeName("description")
            writeValue("first message")
            endObject()
            writeName("nestedArray")
            beginArray()
            writeValue(1)
            writeValue(2.2f)
            writeValue(-3.3333)
            endArray()
            writeName("integerId")
            writeValue(1)
            endObject()

            beginObject()
            writeName("id")
            writeValue("msg2")
            writeName("meta")
            beginObject()
            writeName("description")
            writeValue("second message")
            endObject()
            writeName("nestedArray")
            beginArray()
            writeValue(4)
            writeValue(5)
            endArray()
            writeName("integerId")
            writeValue(2)
            endObject()

            endArray()
        }
        val actual = writer.bytes?.decodeToString()

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
    fun testPretty() {
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

    @Test
    fun testInvalidClose() {
        assertFailsWith<IllegalStateException>("end empty array") {
            jsonStreamWriter().apply {
                beginArray()
                endObject()
            }
        }

        assertFailsWith<IllegalStateException>("end non-empty array") {
            jsonStreamWriter().apply {
                beginArray()
                writeValue(1)
                endObject()
            }
        }

        assertFailsWith<IllegalStateException>("end empty object") {
            jsonStreamWriter().apply {
                beginObject()
                endArray()
            }
        }

        assertFailsWith<IllegalStateException>("end object key no value") {
            jsonStreamWriter().apply {
                beginObject()
                writeName("foo")
                endObject()
            }
        }

        assertFailsWith<IllegalStateException>("end non empty object") {
            jsonStreamWriter().apply {
                beginObject()
                writeName("foo")
                writeValue(1)
                endArray()
            }
        }

        assertFailsWith<IllegalStateException>("end array without start") {
            jsonStreamWriter().apply { endObject() }
        }

        assertFailsWith<IllegalStateException>("end object without start") {
            jsonStreamWriter().apply { endArray() }
        }
    }
}
