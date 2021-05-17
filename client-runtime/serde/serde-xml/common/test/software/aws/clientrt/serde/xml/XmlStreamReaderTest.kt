/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.DeserializationException
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.*

@OptIn(ExperimentalStdlibApi::class)
class XmlStreamReaderTest {
    @Test
    fun itDeserializesXml() = runSuspendTest {
        val payload = """
            <root>
                <x>1</x>
                <y>2</y>
            </root>
        """.encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        val expected = listOf(
            XmlToken.BeginElement(1, "root"),
            XmlToken.BeginElement(2, "x"),
            XmlToken.Text(2, "1"),
            XmlToken.EndElement(2, "x"),
            XmlToken.BeginElement(2, "y"),
            XmlToken.Text(2, "2"),
            XmlToken.EndElement(2, "y"),
            XmlToken.EndElement(1, "root"),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun itDeserializesXmlWithEscapedCharacters() = runSuspendTest {
        val payload = """<root>&lt;string&gt;</root>""".trimIndent().encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        val expected = listOf(
            XmlToken.BeginElement(1, "root"),
            XmlToken.Text(1, "<string>"),
            XmlToken.EndElement(1, "root"),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun itDeserializesXmlWithAttributes() = runSuspendTest {
        val payload = """
            <batch>
                <add id="tt0484562">
                    <field name="title">The Seeker: The Dark Is Rising</field>
                </add>
                <delete id="tt0301199" />
            </batch>
        """.encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        val expected = listOf(
            XmlToken.BeginElement(1, "batch"),
            XmlToken.BeginElement(2, "add", mapOf(XmlToken.QualifiedName("id") to "tt0484562")),
            XmlToken.BeginElement(3, "field", mapOf(XmlToken.QualifiedName("name") to "title")),
            XmlToken.Text(3, "The Seeker: The Dark Is Rising"),
            XmlToken.EndElement(3, "field"),
            XmlToken.EndElement(2, "add"),
            XmlToken.BeginElement(2, "delete", mapOf(XmlToken.QualifiedName("id") to "tt0301199")),
            XmlToken.EndElement(2, "delete"),
            XmlToken.EndElement(1, "batch"),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun garbageInGarbageOut() = runSuspendTest {
        val payload = """you try to parse me once, jokes on me..try twice jokes on you bucko.""".trimIndent().encodeToByteArray()
        assertFailsWith(DeserializationException::class) { xmlStreamReader(payload).allTokens() }
    }

    @Test
    fun itIgnoresXmlComments() = runSuspendTest {
        val payload = """
               <?xml version="1.0" encoding="UTF-8"?>
               <!--
                 ~ Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
                 ~ SPDX-License-Identifier: Apache-2.0.
                 -->
                
               <payload>
                    <x value="1" />
                    <y value="2" />
               </payload>
        """.trimIndent().encodeToByteArray()

        val actual = xmlStreamReader(payload).allTokens()
        println(actual)

        assertEquals(actual.size, 6)
        assertTrue(actual.first() is XmlToken.BeginElement)
        assertTrue((actual.first() as XmlToken.BeginElement).name.local == "payload")
    }

    @Test
    fun itHandlesNilNodeValues() = runSuspendTest {
        val payload = """<null></null>""".encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()
        val expected = listOf(
            XmlToken.BeginElement(1, "null"),
            XmlToken.EndElement(1, "null"),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun kitchenSink() = runSuspendTest {
        val payload = """
        <root>
          <num>1</num>    
          <str>string</str>
          <list>
            <value>1</value>
            <value>2.3456</value>
            <value>3</value>
          </list>
          <nested>
            <l2>
              <list>
                <x>x</x>
                <value>true</value>
              </list>
            </l2>
            <falsey>false</falsey>
          </nested>
          <null></null>
        </root>
        """.trimIndent().encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()
        val expected = listOf(
            XmlToken.BeginElement(1, "root"),
            XmlToken.BeginElement(2, "num"),
            XmlToken.Text(2, "1"),
            XmlToken.EndElement(2, "num"),
            XmlToken.BeginElement(2, "str"),
            XmlToken.Text(2, "string"),
            XmlToken.EndElement(2, "str"),
            XmlToken.BeginElement(2, "list"),
            XmlToken.BeginElement(3, "value"),
            XmlToken.Text(3, "1"),
            XmlToken.EndElement(3, "value"),
            XmlToken.BeginElement(3, "value"),
            XmlToken.Text(3, "2.3456"),
            XmlToken.EndElement(3, "value"),
            XmlToken.BeginElement(3, "value"),
            XmlToken.Text(3, "3"),
            XmlToken.EndElement(3, "value"),
            XmlToken.EndElement(2, "list"),
            XmlToken.BeginElement(2, "nested"),
            XmlToken.BeginElement(3, "l2"),
            XmlToken.BeginElement(4, "list"),
            XmlToken.BeginElement(5, "x"),
            XmlToken.Text(5, "x"),
            XmlToken.EndElement(5, "x"),
            XmlToken.BeginElement(5, "value"),
            XmlToken.Text(5, "true"),
            XmlToken.EndElement(5, "value"),
            XmlToken.EndElement(4, "list"),
            XmlToken.EndElement(3, "l2"),
            XmlToken.BeginElement(3, "falsey"),
            XmlToken.Text(3, "false"),
            XmlToken.EndElement(3, "falsey"),
            XmlToken.EndElement(2, "nested"),
            XmlToken.BeginElement(2, "null"),
            XmlToken.EndElement(2, "null"),
            XmlToken.EndElement(1, "root"),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun itSkipsValuesRecursively() = runSuspendTest {
        val payload = """
            <payload>
                <x>1></x>
                <unknown>
                    <a>a</a>
                    <b>b</b>
                    <c>
                        <list>
                            <element>d</element>
                            <element>e</element>
                            <element>f</element>
                        </list>
                    </c>
                    <g>
                        <h>h</h>
                        <i>i</i>
                    </g>
                </unknown>
                <y>2></y>
            </payload>
        """.encodeToByteArray()
        val reader = xmlStreamReader(payload)
        // skip x
        reader.apply {
            nextToken() // begin obj
            nextToken() // x
            nextToken() // value
            nextToken() // end x
        }

        val nt = reader.peek()
        assertTrue(nt is XmlToken.BeginElement)

        assertEquals("unknown", nt.name.local)
        reader.skipNext()

        val y = reader.nextToken() as XmlToken.BeginElement
        assertEquals("y", y.name.local)
    }

    @Test
    fun itSkipsSimpleValues() = runSuspendTest {
        val payload = """
            <payload>
                <x>1</x>
                <z>unknown</z>
                <y>2</y>
            </payload>
        """.trimIndent().encodeToByteArray()
        val reader = xmlStreamReader(payload)
        // skip x
        reader.apply {
            nextToken() // begin obj
            nextToken() // x
        }
        reader.skipNext()

        assertTrue(reader.peek() is XmlToken.BeginElement)

        val zElement = reader.nextToken() as XmlToken.BeginElement
        assertEquals("z", zElement.name.local)
        reader.skipNext()

        val yElement = reader.nextToken() as XmlToken.BeginElement
        assertEquals("y", yElement.name.local)
    }

    @Test
    fun itHandlesNestedNodesOfSameName() = runSuspendTest {
        val payload = """
            <Response>
               <Response>abc</Response>
               <A/>
            </Response>
        """.trimIndent().encodeToByteArray()

        val actual = xmlStreamReader(payload).allTokens()
        val expected = listOf(
            XmlToken.BeginElement(1, "Response"),
            XmlToken.BeginElement(2, "Response"),
            XmlToken.Text(2, "abc"),
            XmlToken.EndElement(2, "Response"),
            XmlToken.BeginElement(2, "A"),
            XmlToken.EndElement(2, "A"),
            XmlToken.EndElement(1, "Response"),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun itPeeksWithoutImpactingNestingLevel() = runSuspendTest {
        val payload = """
           <l1>
               <l2>
                   <l3>text</l3>
               </l2>
           </l1>
        """.trimIndent().encodeToByteArray()
        val reader = xmlStreamReader(payload)

        assertTrue(reader.lastToken?.depth == 1, "Expected to start at level 1")
        var peekedToken = reader.peek()
        assertTrue(peekedToken is XmlToken.BeginElement)
        assertTrue(peekedToken.name.local == "l1")
        assertTrue(reader.lastToken?.depth == 1, "Expected peek to not effect level")
        reader.nextToken() // consumed l1

        peekedToken = reader.nextToken() // consumed l2
        assertTrue(reader.lastToken?.depth == 2, "Expected level 2")
        assertTrue(peekedToken is XmlToken.BeginElement)
        assertTrue(peekedToken.name.local == "l2")
        reader.peek()
        assertTrue(reader.lastToken?.depth == 2, "Expected peek to not effect level")

        peekedToken = reader.nextToken()
        assertTrue(reader.lastToken?.depth == 3, "Expected level 3")
        assertTrue(peekedToken is XmlToken.BeginElement)
        assertTrue(peekedToken.name.local == "l3")
        reader.peek()
        assertTrue(reader.lastToken?.depth == 3, "Expected peek to not effect level")
    }

    @Test
    fun itHandlesNamespaceDefaults() = runSuspendTest {
        val payload = """
            <MyStructure xmlns="http://foo.com">
                <foo>bar</foo>
            </MyStructure>
        """.encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        val expected = listOf(
            // root element belongs to default namespace declared
            XmlToken.BeginElement(1, XmlToken.QualifiedName("MyStructure"), nsDeclarations = listOf(XmlToken.Namespace("http://foo.com"))),
            XmlToken.BeginElement(2, XmlToken.QualifiedName("foo")),
            XmlToken.Text(2, "bar"),
            XmlToken.EndElement(2, XmlToken.QualifiedName("foo")),
            XmlToken.EndElement(1, XmlToken.QualifiedName("MyStructure")),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesNamespacePrefixes() = runSuspendTest {
        val payload = """
            <MyStructure xmlns:baz="http://foo.com">
                <foo>what</foo>
                <baz:bar>yeah</baz:bar>
            </MyStructure>
        """.encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        val expected = listOf(
            XmlToken.BeginElement(1, XmlToken.QualifiedName("MyStructure"), nsDeclarations = listOf(XmlToken.Namespace("http://foo.com", "baz"))),
            XmlToken.BeginElement(2, "foo"),
            XmlToken.Text(2, "what"),
            XmlToken.EndElement(2, "foo"),
            XmlToken.BeginElement(2, XmlToken.QualifiedName("bar", prefix = "baz")),
            XmlToken.Text(2, "yeah"),
            XmlToken.EndElement(2, XmlToken.QualifiedName("bar", prefix = "baz")),
            XmlToken.EndElement(1, "MyStructure"),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesNamespacedAttributes() = runSuspendTest {
        val payload = """
            <MyStructure xmlns:baz="http://foo.com">
                <foo baz:k1="v1"></foo>
            </MyStructure>
        """.encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        val expected = listOf(
            XmlToken.BeginElement(1, XmlToken.QualifiedName("MyStructure"), nsDeclarations = listOf(XmlToken.Namespace("http://foo.com", "baz"))),
            XmlToken.BeginElement(2, "foo", attributes = mapOf(XmlToken.QualifiedName("k1", "baz") to "v1")),
            XmlToken.EndElement(2, "foo"),
            XmlToken.EndElement(1, "MyStructure"),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun itSubTrees() = runSuspendTest {
        val payload = """
            <root>
                <a>
                    <n>subtree 1</n>
                </a>
                <b>
                    <n>subtree 2</n>
                </b>
                <c>
                    <n>subtree 3</n>
                </c>
            </root>
        """.encodeToByteArray()
        var unit = xmlStreamReader(payload)

        val token = unit.nextToken()
        assertTrue(token is XmlToken.BeginElement)
        assertTrue(token.name.local == "root")

        var subTree1 = unit.subTreeReader()
        var subTree1Elements = subTree1.allTokens()

        val expected1 = listOf(
            XmlToken.BeginElement(2, "a"),
            XmlToken.BeginElement(3, "n"),
            XmlToken.Text(3, "subtree 1"),
            XmlToken.EndElement(3, "n"),
            XmlToken.EndElement(2, "a"),
            XmlToken.BeginElement(2, "b"),
            XmlToken.BeginElement(3, "n"),
            XmlToken.Text(3, "subtree 2"),
            XmlToken.EndElement(3, "n"),
            XmlToken.EndElement(2, "b"),
            XmlToken.BeginElement(2, "c"),
            XmlToken.BeginElement(3, "n"),
            XmlToken.Text(3, "subtree 3"),
            XmlToken.EndElement(3, "n"),
            XmlToken.EndElement(2, "c"),
        )
        assertEquals(expected1, subTree1Elements)

        unit = xmlStreamReader(payload)
        repeat(2) { unit.nextToken() }

        subTree1 = unit.subTreeReader()
        subTree1Elements = subTree1.allTokens()

        val expected2 = listOf(
            XmlToken.BeginElement(3, "n"),
            XmlToken.Text(3, "subtree 1"),
            XmlToken.EndElement(3, "n"),
        )
        assertEquals(expected2, subTree1Elements)

        unit = xmlStreamReader(payload)
        repeat(3) { unit.nextToken() }

        subTree1 = unit.subTreeReader()
        subTree1Elements = subTree1.allTokens()

        val expected3 = listOf<XmlToken>()
        assertEquals(expected3, subTree1Elements)
    }

    @Test
    fun itHandlesPeekingMultipleLevels() = runSuspendTest {
        val payload = """
            <r>
                <a>
                    <b>
                        <c/>
                    </b>
                </a>
            </r>
        """.encodeToByteArray()
        val actual = xmlStreamReader(payload)

        val rTokenPeek = actual.peek(1)
        val aToken = actual.peek(2)
        val rTokenTake = actual.nextToken()

        assertTrue(rTokenPeek is XmlToken.BeginElement)
        assertTrue(rTokenPeek.name.local == "r")

        assertTrue(aToken is XmlToken.BeginElement)
        assertTrue(aToken.name.local == "a")

        assertTrue(rTokenTake is XmlToken.BeginElement)
        assertTrue(rTokenTake.name.local == "r")

        val bToken = actual.peek(2)
        assertTrue(bToken is XmlToken.BeginElement)
        assertTrue(bToken.name.local == "b")

        val aTokenTake = actual.nextToken()
        assertTrue(aTokenTake is XmlToken.BeginElement)
        assertTrue(aTokenTake.name.local == "a")

        val aCloseToken = actual.peek(4)
        assertTrue(aCloseToken is XmlToken.EndElement)
        assertTrue(aTokenTake.name.local == "a")

        val restOfTokens = actual.allTokens()
        assertEquals(restOfTokens.size, 6)
    }

    @Test
    fun itHandlesSeekingToNodes() = runSuspendTest {
        val payload = """
            <r>
                <a a1="asdf">
                    <b>
                        <c>some text</c>
                    </b>
                </a>
            </r>
        """.encodeToByteArray()
        var unit = xmlStreamReader(payload)

        // match text node contents
        val textNode = unit.seek<XmlToken.Text> { text -> text.value == "some text" }
        assertTrue(textNode is XmlToken.Text)
        assertTrue(textNode.value == "some text")

        unit = xmlStreamReader(payload)
        // match begin node of depth 2
        val l2Node = unit.seek<XmlToken.BeginElement> { it.depth == 2 }
        assertTrue(l2Node is XmlToken.BeginElement)
        assertTrue(l2Node.name.local == "a")

        // verify next token is correct
        val nextNode = unit.nextToken()
        assertTrue(nextNode is XmlToken.BeginElement)
        assertTrue(nextNode.name.local == "b")

        // verify no match produces null
        unit = xmlStreamReader(payload)
        val noNode = unit.seek<XmlToken.BeginElement> { it.depth == 9 }
        assertNull(noNode)
        assertNull(unit.nextToken())
    }

    @Test
    fun itThrowsErrorsOnInvalidXmlSequences() = runSuspendTest {
        val invalidTextList = listOf(
            """&lte;""",
            "&lte;",
            "&lt",
            "&#Q1234;",
            "&#3.14;",
            "&#xZZ",
            "here is a & but without an escape sequence..."
        )

        invalidTextList.forEach { testCase ->
            val input = "<a>$testCase</a>".encodeToByteArray()
            // FIXME ~ XPP throws NPE here due to invalid internal state.  Once we have a better
            //  XML parser we should expect a specific parse exception.
            assertFails {
                val actual = xmlStreamReader(input)
                actual.allTokens()
            }
        }
    }
}

suspend fun XmlStreamReader.allTokens(): List<XmlToken> {
    val tokenList = mutableListOf<XmlToken>()
    var nextToken: XmlToken?
    do {
        nextToken = this.nextToken()
        if (nextToken != null) tokenList.add(nextToken)
    } while (nextToken != null)

    return tokenList
}
