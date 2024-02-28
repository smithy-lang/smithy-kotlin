/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.tests.serde

import aws.smithy.kotlin.runtime.serde.xml.xmlTagReader
import aws.smithy.kotlin.tests.serde.xml.model.FooEnum
import aws.smithy.kotlin.tests.serde.xml.model.StructType
import aws.smithy.kotlin.tests.serde.xml.model.UnionType
import aws.smithy.kotlin.tests.serde.xml.serde.deserializeStructTypeDocument
import aws.smithy.kotlin.tests.serde.xml.serde.serializeStructTypeDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class XmlMapTest : AbstractXmlTest() {
    @Test
    fun testNormalMap() {
        val expected = StructType {
            normalMap = mapOf(
                "foo" to "bar",
                "baz" to "quux",
            )
        }
        val payload = """
            <StructType>
                <normalMap>
                    <entry>
                        <key>foo</key>
                        <value>bar</value>
                    </entry>
                    <entry>
                        <key>baz</key>
                        <value>quux</value>
                    </entry>
                </normalMap>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testSparseMap() {
        val expected = StructType {
            sparseMap = mapOf(
                "foo" to "bar",
                "null" to null,
                "baz" to "quux",
            )
        }
        val payload = """
            <StructType>
                <sparseMap>
                    <entry>
                        <key>foo</key>
                        <value>bar</value>
                    </entry>
                    <entry>
                        <key>null</key>
                        <value></value>
                    </entry>
                    <entry>
                        <key>baz</key>
                        <value>quux</value>
                    </entry>
                </sparseMap>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testNestedMap() {
        val expected = StructType {
            nestedMap = mapOf(
                "foo" to mapOf(
                    "k1" to "v1",
                    "k2" to "v2",
                ),
                "bar" to mapOf(
                    "k3" to "v3",
                    "k4" to "v4",
                ),
            )
        }
        val payload = """
            <StructType>
                <nestedMap>
                    <entry>
                        <key>foo</key>
                        <value>
                            <entry>
                                <key>k1</key>
                                <value>v1</value>
                            </entry>
                            <entry>
                                <key>k2</key>
                                <value>v2</value>
                            </entry>
                        </value>
                    </entry>
                    <entry>
                        <key>bar</key>
                        <value>
                            <entry>
                                <key>k3</key>
                                <value>v3</value>
                            </entry>
                            <entry>
                                <key>k4</key>
                                <value>v4</value>
                            </entry>
                        </value>
                    </entry>
                </nestedMap>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testMapWithRenamedMember() {
        val expected = StructType {
            renamedMemberMap = mapOf(
                "foo" to "bar",
                "baz" to "quux",
            )
        }
        val payload = """
            <StructType>
                <renamedMemberMap>
                    <entry>
                        <aKey>foo</aKey>
                        <aValue>bar</aValue>
                    </entry>
                    <entry>
                        <aKey>baz</aKey>
                        <aValue>quux</aValue>
                    </entry>
                </renamedMemberMap>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testFlatMap() {
        val expected = StructType {
            flatEnumMap = mapOf(
                "foo" to FooEnum.Foo,
                "bar" to FooEnum.Bar,
            )
        }
        val payload = """
            <StructType>
                <flatenummap>
                    <key>foo</key>
                    <value>Foo</value>
                </flatenummap>
                <flatenummap>
                    <key>bar</key>
                    <value>Bar</value>
                </flatenummap>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testInterspersedFlatMaps() {
        // see https://github.com/awslabs/aws-sdk-kotlin/issues/1220
        val expected = StructType {
            flatEnumMap = mapOf(
                "foo" to FooEnum.Foo,
                "bar" to FooEnum.Bar,
            )
            unionField = UnionType.Struct(
                StructType {
                    normalMap = mapOf("k1" to "v1", "k2" to "v2")
                    flatEnumMap = mapOf("inner" to FooEnum.Baz)
                },
            )
        }
        val payload = """
            <StructType>
                <flatenummap>
                    <key>foo</key>
                    <value>Foo</value>
                </flatenummap>
                <extra></extra>
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
                        <flatenummap>
                            <key>inner</key>
                            <value>Baz</value>
                        </flatenummap>
                    </struct>
                </unionField>
                <flatenummap>
                    <key>bar</key>
                    <value>Bar</value>
                </flatenummap>
            </StructType>
        """.trimIndent()

        // we don't round trip this because the format isn't going to match
        val reader = xmlTagReader(payload.encodeToByteArray())
        val actualDeserialized = deserializeStructTypeDocument(reader)
        assertEquals(expected, actualDeserialized)
    }

    @Test
    fun testEnumValueMap() {
        val expected = StructType {
            enumValueMap = mapOf(
                "foo" to FooEnum.Foo,
                "bar" to FooEnum.Bar,
            )
        }
        val payload = """
            <StructType>
                <enumValueMap>
                    <entry>
                        <key>foo</key>
                        <value>Foo</value>
                    </entry>
                    <entry>
                        <key>bar</key>
                        <value>Bar</value>
                    </entry>
                </enumValueMap>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testEnumKeyMap() {
        // see also https://github.com/awslabs/smithy-kotlin/issues/1045
        val expected = StructType {
            enumKeyMap = mapOf(
                FooEnum.Foo.value to 1,
                "Bar" to 2,
                "Unknown" to 3,
            )
        }
        val payload = """
            <StructType>
                <enumKeyMap>
                    <entry>
                        <key>Foo</key>
                        <value>1</value>
                    </entry>
                    <entry>
                        <key>Bar</key>
                        <value>2</value>
                    </entry>
                    <entry>
                        <key>Unknown</key>
                        <value>3</value>
                    </entry>
                </enumKeyMap>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }
}
