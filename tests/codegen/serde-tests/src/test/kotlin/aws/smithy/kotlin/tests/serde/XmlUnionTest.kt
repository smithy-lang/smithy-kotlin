/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.tests.serde

import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.tests.serde.xml.model.StructType
import aws.smithy.kotlin.tests.serde.xml.model.UnionType
import aws.smithy.kotlin.tests.serde.xml.serde.deserializeStructTypeDocument
import aws.smithy.kotlin.tests.serde.xml.serde.serializeStructTypeDocument
import kotlin.test.Test

class XmlUnionTest : AbstractXmlTest() {
    @Test
    fun testString() {
        val expected = StructType {
            unionField = UnionType.StrField("a string")
        }
        val payload = """
            <StructType>
                <unionField>
                    <strField>a string</strField>
                </unionField>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testByte() {
        val expected = StructType {
            unionField = UnionType.ByteField(1)
        }
        val payload = """
            <StructType>
                <unionField>
                    <byteField>1</byteField>
                </unionField>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testInt() {
        val expected = StructType {
            unionField = UnionType.IntField(1)
        }
        val payload = """
            <StructType>
                <unionField>
                    <intField>1</intField>
                </unionField>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testLong() {
        val expected = StructType {
            unionField = UnionType.LongField(1)
        }
        val payload = """
            <StructType>
                <unionField>
                    <longField>1</longField>
                </unionField>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testTimestamp() {
        val expected = StructType {
            unionField = UnionType.DateTimeField(
                Instant.fromIso8601("2020-10-16T15:46:24.982Z"),
            )
        }
        val payload = """
            <StructType>
                <unionField>
                    <dateTimeField>2020-10-16T15:46:24.982Z</dateTimeField>
                </unionField>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testNormalList() {
        val expected = StructType {
            unionField = UnionType.NormalList(listOf("foo", "bar"))
        }

        val payload = """
            <StructType>
                <unionField>
                    <normalList>
                        <member>foo</member>
                        <member>bar</member>
                    </normalList>
                </unionField>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testNormalMap() {
        val expected = StructType {
            unionField = UnionType.NormalMap(
                mapOf(
                    "k1" to "v1",
                    "k2" to "v2",
                ),
            )
        }
        val payload = """
            <StructType>
                <unionField>
                    <normalMap>
                        <entry>
                            <key>k1</key>
                            <value>v1</value>
                        </entry>
                        <entry>
                            <key>k2</key>
                            <value>v2</value>
                        </entry>
                    </normalMap>
                </unionField>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    // FIXME - https://github.com/awslabs/smithy-kotlin/issues/1040
    // @Test
    // fun testUnitField() { }

    @Test
    fun testStruct() {
        val expected = StructType {
            unionField = UnionType.Struct(
                StructType {
                    normalMap = mapOf("k1" to "v1", "k2" to "v2")
                    strField = "a string"
                },
            )
        }
        val payload = """
            <StructType>
                <unionField>
                    <struct>
                        <normalMap>
                            <entry>
                                <key>k1</key>
                                <value>v1</value>
                            </entry>
                            <entry>
                                <key>k2</key>
                                <value>v2</value>
                            </entry>
                        </normalMap>
                        <strField>a string</strField>
                    </struct>
                </unionField>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }
}
