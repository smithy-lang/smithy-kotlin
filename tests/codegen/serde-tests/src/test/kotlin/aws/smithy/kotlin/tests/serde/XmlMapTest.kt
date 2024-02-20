/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.tests.serde

import aws.smithy.kotlin.tests.serde.xml.model.FooEnum
import aws.smithy.kotlin.tests.serde.xml.model.StructType
import aws.smithy.kotlin.tests.serde.xml.serde.deserializeStructTypeDocument
import aws.smithy.kotlin.tests.serde.xml.serde.serializeStructTypeDocument
import kotlin.test.Test

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
}
