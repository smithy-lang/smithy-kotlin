/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.serde.CharStream
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonStreamReaderTest {
    @Test
    fun itDeserializesObjects() = runSuspendTest {
        // language=JSON
        val actual = """ 
            {
                "x": 1,
                "y": "2"
            }
        """.allTokens()

        actual.shouldContainExactly(
            JsonToken.BeginObject,
            JsonToken.Name("x"),
            JsonToken.Number("1"),
            JsonToken.Name("y"),
            JsonToken.String("2"),
            JsonToken.EndObject,
            JsonToken.EndDocument
        )
    }

    @Test
    fun isDeserializesArrays() = runSuspendTest {
        // language=JSON
        val actual = """[ "hello", "world" ]""".allTokens()

        actual.shouldContainExactly(
            JsonToken.BeginArray,
            JsonToken.String("hello"),
            JsonToken.String("world"),
            JsonToken.EndArray,
            JsonToken.EndDocument
        )
    }

    @Test
    fun itDeserializesSingleScalarStrings() = runSuspendTest {
        // language=JSON
        val actual = "\"hello\"".allTokens()
        actual.shouldContainExactly(
            JsonToken.String("hello"),
            JsonToken.EndDocument
        )
    }

    @Test
    fun itDeserializesSingleScalarNumbers() = runSuspendTest {
        // language=JSON
        val actual = "1.2".allTokens()
        actual.shouldContainExactly(
            JsonToken.Number("1.2"),
            JsonToken.EndDocument
        )
    }

    @Test
    fun itCanHandleAllDataTypes() = runSuspendTest {
        // language=JSON
        val actual = """[ "hello", true, false, 1.0, 1, -34.234e3, null ]""".allTokens()

        actual.shouldContainExactly(
            JsonToken.BeginArray,
            JsonToken.String("hello"),
            JsonToken.Bool(true),
            JsonToken.Bool(false),
            JsonToken.Number("1.0"),
            JsonToken.Number("1"),
            JsonToken.Number("-34.234e3"),
            JsonToken.Null,
            JsonToken.EndArray,
            JsonToken.EndDocument
        )
    }

    @Test
    fun canHandleNesting() = runSuspendTest {
        // language=JSON
        val actual = """
        [
          "hello",
          {
            "foo": [
              20,
              true,
              null
            ],
            "bar": "value"
          }
        ]""".allTokens()

        actual.shouldContainExactly(
            JsonToken.BeginArray,
            JsonToken.String("hello"),
            JsonToken.BeginObject,
            JsonToken.Name("foo"),
            JsonToken.BeginArray,
            JsonToken.Number("20"),
            JsonToken.Bool(true),
            JsonToken.Null,
            JsonToken.EndArray,
            JsonToken.Name("bar"),
            JsonToken.String("value"),
            JsonToken.EndObject,
            JsonToken.EndArray,
            JsonToken.EndDocument
        )
    }

    @Test
    fun itSkipsValuesRecursively() = runSuspendTest {
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
        """.trimIndent()
        val reader = newReader(payload)
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
    fun itSkipsSimpleValues() = runSuspendTest {
        val payload = """
        {
            "x": 1,
            "z": "unknown",
            "y": 2
        }
        """.trimIndent()
        val reader = newReader(payload)
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

    @Test
    fun kitchenSink() = runSuspendTest {
        val actual = """
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
        """.trimIndent().allTokens()

        actual.shouldContainExactly(
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
    }
}

private suspend fun String.allTokens(): List<JsonToken> {
    val reader = newReader(this)
    val tokens = mutableListOf<JsonToken>()
    while (true) {
        val token = reader.nextToken()
        tokens.add(token)
        if (token is JsonToken.EndDocument) {
            return tokens
        }
    }
}

private fun newReader(contents: String): JsonStreamReader = JsonLexer(CharStream(SdkByteReadChannel(contents.encodeToByteArray())))
