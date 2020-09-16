/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.json

import kotlin.test.Test
import kotlin.test.assertEquals

fun JsonStreamReader.allTokens(): List<JsonToken> {
    val tokens = mutableListOf<JsonToken>()
    while (true) {
        val token = nextToken()
        tokens.add(token)
        if (token is JsonToken.EndDocument) {
            break
        }
    }
    return tokens
}

fun assertTokensAreEqual(expected: List<JsonToken>, actual: List<JsonToken>) {
    assertEquals(expected.size, actual.size, "unbalanced tokens")
    val pairs = expected.zip(actual)
    pairs.forEach { (exp, act) ->
        assertEquals(exp, act)
    }
}

@OptIn(ExperimentalStdlibApi::class)
class JsonStreamReaderTest {
    @Test
    fun `it deserializes objects`() {
        val payload = """
            {
                "x": 1,
                "y": "2"
            }
        """.trimIndent().encodeToByteArray()
        val actual = jsonStreamReader(payload).allTokens()

        val expected = listOf(
            JsonToken.BeginObject,
            JsonToken.Name("x"),
            JsonToken.Number("1"),
            JsonToken.Name("y"),
            JsonToken.String("2"),
            JsonToken.EndObject,
            JsonToken.EndDocument
        )
        assertTokensAreEqual(expected, actual)
    }

    @Test
    fun `kitchen sink`() {
        val payload = """
        {
            "num": 1,
            "str": "string",
            "list": [1,2.3456,"3"],
            "nested": {
                "l2": [
                    {
                        "x": "x",
                        "bool": true
                    }
                ],
                "falsey": false
            },
            "null": null
        }
        """.trimIndent().encodeToByteArray()
        val actual = jsonStreamReader(payload).allTokens()
        val expected = listOf(
            JsonToken.BeginObject,
            JsonToken.Name("num"),
            JsonToken.Number("1"),
            JsonToken.Name("str"),
            JsonToken.String("string"),
            JsonToken.Name("list"),
            JsonToken.BeginArray,
            JsonToken.Number("1"),
            JsonToken.Number("2.3456"),
            JsonToken.String("3"),
            JsonToken.EndArray,
            JsonToken.Name("nested"),
            JsonToken.BeginObject,
            JsonToken.Name("l2"),
            JsonToken.BeginArray,
            JsonToken.BeginObject,
            JsonToken.Name("x"),
            JsonToken.String("x"),
            JsonToken.Name("bool"),
            JsonToken.Bool(true),
            JsonToken.EndObject,
            JsonToken.EndArray,
            JsonToken.Name("falsey"),
            JsonToken.Bool(false),
            JsonToken.EndObject,
            JsonToken.Name("null"),
            JsonToken.Null,
            JsonToken.EndObject,
            JsonToken.EndDocument
        )

        assertTokensAreEqual(expected, actual)
    }

    @Test
    fun `it skips values recursively`() {
        val payload = """
        {
            "x": 1,
            "unknown": {
                "a": "a",
                "b": "b",
                "c": ["d", "e", "f"],
                "g": {
                    "h": "h",
                    "i": "i"
                }
             },
            "y": 2
        }
        """.trimIndent().encodeToByteArray()
        val reader = jsonStreamReader(payload)
        // skip x
        reader.apply {
            nextToken() // begin obj
            nextToken() // x
            nextToken() // value
        }

        val name = reader.nextToken() as JsonToken.Name
        assertEquals("unknown", name.value)
        reader.skipNext()

        val y = reader.nextToken() as JsonToken.Name
        assertEquals("y", y.value)
    }

    @Test
    fun `it skips simple values`() {
        val payload = """
        {
            "x": 1,
            "z": "unknown",
            "y": 2
        }
        """.trimIndent().encodeToByteArray()
        val reader = jsonStreamReader(payload)
        // skip x
        reader.apply {
            nextToken() // begin obj
            nextToken() // x
        }
        reader.skipNext()

        val name = reader.nextToken() as JsonToken.Name
        assertEquals("z", name.value)
        reader.skipNext()

        val y = reader.nextToken() as JsonToken.Name
        assertEquals("y", y.value)
    }
}
