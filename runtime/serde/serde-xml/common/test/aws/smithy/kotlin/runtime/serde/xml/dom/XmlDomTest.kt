/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.xml.dom

import aws.smithy.kotlin.runtime.serde.xml.XmlToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class XmlDomTest {
    @Test
    fun smokeTest() {
        val payload = """
            <Foo>
                <Bar>b1</Bar>
                <Bar>b2</Bar>
            </Foo>
        """

        val dom = XmlNode.parse(payload.encodeToByteArray())

        assertEquals(XmlToken.QualifiedName("Foo"), dom.name)
        assertEquals(1, dom.children.size)
        val bars = dom.children["Bar"]
        assertNotNull(bars)
        assertEquals(2, bars.size)
        assertEquals("Bar", bars[0].name.local)
        assertEquals("b1", bars[0].text)

        assertEquals("Bar", bars[1].name.local)
        assertEquals("b2", bars[1].text)
    }

    @Test
    fun testBasicRoundTripToXmlString() {
        val payload = "<Foo><Bar>b1</Bar><Bar>b2</Bar></Foo>"
        val dom = XmlNode.parse(payload.encodeToByteArray())
        val actual = dom.toXmlString(false)
        assertEquals(payload, actual)
    }

    @Test
    fun testBasicToXmlStringPretty() {
        val expected = """
            <Foo>
                <Bar>b1</Bar>
                <Bar>b2</Bar>
            </Foo>
        """.trimIndent()

        val dom = XmlNode.parse(expected.encodeToByteArray())
        val actual = dom.toXmlString(true)
        assertEquals(expected, actual)
    }

    @Test
    fun testToXmlStringPretty() {
        val expected = """
            <Foo>
                <Bar>b1</Bar>
                <Bar>
                    <Nested attr1="baz">quux</Nested>
                    <Foo>
                        <Bar>
                            <Nested attr1="spaz">qux</Nested>
                        </Bar>
                    </Foo>
                </Bar>
            </Foo>
        """.trimIndent()

        val dom = XmlNode.parse(expected.encodeToByteArray())
        val actual = dom.toXmlString(true)
        assertEquals(expected, actual)
    }

    @Test
    fun xmlNamespaceTest() {
        val payload = """
            <Foo xmlns="http://foo.com">
                <Bar>b1</Bar>
                <Bar>b2</Bar>
            </Foo>
        """.trimIndent()

        val dom = XmlNode.parse(payload.encodeToByteArray())

        assertEquals(XmlToken.QualifiedName("Foo"), dom.name)
        assertEquals(0, dom.attributes.size)

        assertEquals(payload, dom.toXmlString(true))
    }

    @Test
    fun xmlNamespacePrefixTest() {
        val payload = """
            <Foo xmlns:baz="http://foo.com">
                <baz:Bar>b1</baz:Bar>
                <baz:Bar>b2</baz:Bar>
            </Foo>
        """.trimIndent()

        val dom = XmlNode.parse(payload.encodeToByteArray())

        assertEquals(XmlToken.QualifiedName("Foo"), dom.name)
        assertEquals(0, dom.attributes.size)

        assertEquals(payload, dom.toXmlString(true))
    }

    @Test
    fun xmlNamespaceAttributeTest() {
        val payload = """
            <Foo xmlns:baz="http://foo.com">
                <Bar baz:k1="v1"></Bar>
            </Foo>
        """.trimIndent()

        val dom = XmlNode.parse(payload.encodeToByteArray())

        assertEquals(payload, dom.toXmlString(true))
    }
}
