/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml.dom

import software.aws.clientrt.serde.xml.XmlToken
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class XmlDomTest {
    @Test
    fun smokeTest() = runSuspendTest {
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
    fun testBasicRoundTripToXmlString() = runSuspendTest {
        val payload = "<Foo><Bar>b1</Bar><Bar>b2</Bar></Foo>"
        val dom = XmlNode.parse(payload.encodeToByteArray())
        val actual = dom.toXmlString(false)
        assertEquals(payload, actual)
    }

    @Test
    fun testBasicToXmlStringPretty() = runSuspendTest {
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
    fun testToXmlStringPretty() = runSuspendTest {
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
    fun xmlNamespaceTest() = runSuspendTest {
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
    fun xmlNamespacePrefixTest() = runSuspendTest {
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
    fun xmlNamespaceAttributeTest() = runSuspendTest {
        val payload = """
            <Foo xmlns:baz="http://foo.com">
                <Bar baz:k1="v1"></Bar>
            </Foo>
        """.trimIndent()

        val dom = XmlNode.parse(payload.encodeToByteArray())

        assertEquals(payload, dom.toXmlString(true))
    }
}
