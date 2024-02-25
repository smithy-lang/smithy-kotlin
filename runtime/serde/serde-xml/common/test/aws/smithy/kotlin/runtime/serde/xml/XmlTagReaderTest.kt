/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.parseInt
import kotlin.test.*

class XmlTagReaderTest {

    @Test
    fun testNextTag() {
        // inner b could be confused as closing the outer b if depth isn't tracked properly
        val payload = """
            <XmlListsInputOutput>
                    <a/>
                    <b>
                      <c/>
                      <b></b>
                      <here/>
                    </b>
                    <c></c>
                    <d>more</d>
            </XmlListsInputOutput>
        """.encodeToByteArray()
        val scoped = xmlTagReader(payload)
        val expected = listOf("a", "b", "c", "d")
            .map { XmlToken.BeginElement(2, it) }

        expected.forEach { expectedStartTag ->
            val tagReader = assertNotNull(scoped.nextTag())
            assertEquals(expectedStartTag, tagReader.tag)
            tagReader.drop()
        }
    }

    @Test
    fun testNextTagScope() {
        // test scope of each tag reader
        val payload = """
            <Root> 
                <Child1>
                    <x>1</x>
                    <y>2</y>
                </Child1>
                <Child2>
                    <a>3</a>
                    <b>4</b>
                </Child2>
                <Child3 />
                <Child4>
                    <nested>abc</nested>
                </Child4>
            </Root>
           """.encodeToByteArray()
        val scoped = xmlTagReader(payload)
        assertEquals(XmlToken.BeginElement(1, "Root"), scoped.tag)

        val s1 = assertNotNull(scoped.nextTag())
        assertEquals(XmlToken.BeginElement(2, "Child1"), s1.tag)
        val s1Elements = listOf(
            XmlToken.BeginElement(3, "x"),
            XmlToken.Text(3, "1"),
            XmlToken.EndElement(3, "x"),
            XmlToken.BeginElement(3, "y"),
            XmlToken.Text(3, "2"),
            XmlToken.EndElement(3, "y"),
        )
        assertEquals(s1Elements, s1.allTokens())

        val s2 = assertNotNull(scoped.nextTag())
        assertEquals(XmlToken.BeginElement(2, "Child2"), s2.tag)

        val aReader = assertNotNull(s2.nextTag())
        assertEquals(XmlToken.BeginElement(3, "a"), aReader.tag)
        assertNull(aReader.nextTag())

        val bReader = assertNotNull(s2.nextTag())
        assertEquals(XmlToken.BeginElement(3, "b"), bReader.tag)
        assertEquals(XmlToken.Text(3, "4"), bReader.nextToken())
        assertNull(bReader.nextToken())
        bReader.drop()

        // self close token behavior
        val selfCloseReader = assertNotNull(scoped.nextTag())
        assertEquals(emptyList(), selfCloseReader.allTokens())
        selfCloseReader.drop()

        val s4 = assertNotNull(scoped.nextTag())
        assertEquals(XmlToken.BeginElement(2, "Child4"), s4.tag)
    }

    @Test
    fun testData() {
        val payload = """
            <Root> 
                <Child1>
                    <x>1</x>
                    <y>2</y>
                </Child1>
                <Child2>
                    <a>this is an a</a>
                    <unknown>decoder should skip</unknown>
                    <nestedUnknown>
                        <a>ignored a</a>
                        <b>ignored b</b>
                        <c>ignored c</c>
                    </nestedUnknown>
                </Child2>
                <Child3 />
                <Child4>
                    <nested>  </nested>
                </Child4>
            </Root>
           """.encodeToByteArray()

        val decoder = xmlTagReader(payload)
        loop@while (true) {
            val curr = decoder.nextTag() ?: break@loop
            when (curr.tag.name.tag) {
                "Child1" -> {
                    assertEquals(1, curr.nextTag()?.data()?.parseInt()?.getOrNull())
                    assertEquals(2, curr.nextTag()?.data()?.parseInt()?.getOrNull())
                }
                "Child2" -> {
                    assertEquals("this is an a", curr.nextTag()?.data())
                    // intentionally ignore the next tag and don't consume the entire child subtree
                }
                "Child4" -> assertEquals("  ", curr.nextTag()?.data())
                else -> {}
            }
            // consume the current tag entirely before trying to process the next
            curr.drop()
        }
    }
}

fun XmlTagReader.allTokens(): List<XmlToken> {
    val tokenList = mutableListOf<XmlToken>()
    var nextToken: XmlToken?
    do {
        nextToken = this.nextToken()
        if (nextToken != null) tokenList.add(nextToken)
    } while (nextToken != null)

    return tokenList
}
