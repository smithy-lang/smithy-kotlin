/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.DeserializationException
import kotlin.test.*

class XmlStreamReaderTest {
    @Test
    fun itDeserializesXml() {
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
    fun itDeserializesXmlWithEscapedCharacters() {
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
    fun itDeserializesXmlTextWithAmpersands() {
        val payload = """             
            <value>{&quot;Version&quot;:&quot;2008-10-17&quot;,&quot;Id&quot;:&quot;__default_policy_ID&quot;,&quot;Statement&quot;:[{&quot;Sid&quot;:&quot;__default_statement_ID&quot;,&quot;Effect&quot;:&quot;Allow&quot;,&quot;Principal&quot;:{&quot;AWS&quot;:&quot;*&quot;},&quot;Action&quot;:[&quot;SNS:GetTopicAttributes&quot;,&quot;SNS:SetTopicAttributes&quot;,&quot;SNS:AddPermission&quot;,&quot;SNS:RemovePermission&quot;,&quot;SNS:DeleteTopic&quot;,&quot;SNS:Subscribe&quot;,&quot;SNS:ListSubscriptionsByTopic&quot;,&quot;SNS:Publish&quot;,&quot;SNS:Receive&quot;],&quot;Resource&quot;:&quot;arn:aws:sns:us-west-2:406669096152:kg-test&quot;,&quot;Condition&quot;:{&quot;StringEquals&quot;:{&quot;AWS:SourceOwner&quot;:&quot;406669096152&quot;}}}]}</value>            
        """.trimIndent().encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        // Test that input contains a single TEXT
        assertEquals(3, actual.size)
        assertIs<XmlToken.BeginElement>(actual[0])
        assertIs<XmlToken.Text>(actual[1])
        assertIs<XmlToken.EndElement>(actual[2])
        assertTrue((actual[1] as XmlToken.Text).value!!.isNotEmpty())
    }

    @Test
    fun itDeserializesXmlWithAttributes() {
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
    fun garbageInGarbageOut() {
        val payload = """you try to parse me once, jokes on me..try twice jokes on you bucko.""".trimIndent().encodeToByteArray()
        assertFailsWith(DeserializationException::class) { xmlStreamReader(payload).allTokens() }
    }

    @Test
    fun itIgnoresXmlComments() {
        val payload = """
               <?xml version="1.0" encoding="UTF-8"?>
               <!--
                 ~ Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
                 ~ SPDX-License-Identifier: Apache-2.0
                 -->
                
               <payload>
                    <x value="1" />
                    <y value="2" />
               </payload>
        """.trimIndent().encodeToByteArray()

        val actual = xmlStreamReader(payload).allTokens()
        println(actual)

        assertEquals(6, actual.size)
        assertIs<XmlToken.BeginElement>(actual.first())
        assertEquals("payload", (actual.first() as XmlToken.BeginElement).name.local)
    }

    @Test
    fun itHandlesNilNodeValues() {
        val payload = """<null></null>""".encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()
        val expected = listOf(
            XmlToken.BeginElement(1, "null"),
            XmlToken.EndElement(1, "null"),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun kitchenSink() {
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
    fun itSkipsValuesRecursively() {
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
        assertIs<XmlToken.BeginElement>(nt)

        assertEquals("unknown", nt.name.local)
        reader.skipNext()

        val y = reader.nextToken() as XmlToken.BeginElement
        assertEquals("y", y.name.local)
    }

    @Test
    fun itSkipsSimpleValues() {
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

        assertIs<XmlToken.BeginElement>(reader.peek())

        val zElement = reader.nextToken() as XmlToken.BeginElement
        assertEquals("z", zElement.name.local)
        reader.skipNext()

        val yElement = reader.nextToken() as XmlToken.BeginElement
        assertEquals("y", yElement.name.local)
    }

    @Test
    fun itHandlesNestedNodesOfSameName() {
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
    fun itPeeksWithoutImpactingNestingLevel() {
        val payload = """
           <l1>
               <l2>
                   <l3>text</l3>
               </l2>
           </l1>
        """.trimIndent().encodeToByteArray()
        val reader = xmlStreamReader(payload)

        assertNull(reader.lastToken, "Expected to start with null lastToken")
        var peekedToken = reader.peek()
        assertIs<XmlToken.BeginElement>(peekedToken)
        assertEquals("l1", peekedToken.name.local)
        assertNull(reader.lastToken, "Expected peek to not effect lastToken")
        reader.nextToken() // consumed l1
        assertEquals(1, reader.lastToken?.depth, "Expected level 1")

        peekedToken = reader.nextToken() // consumed l2
        assertEquals(2, reader.lastToken?.depth, "Expected level 2")
        assertIs<XmlToken.BeginElement>(peekedToken)
        assertEquals("l2", peekedToken.name.local)
        reader.peek()
        assertEquals(2, reader.lastToken?.depth, "Expected peek to not effect level")

        peekedToken = reader.nextToken()
        assertEquals(3, reader.lastToken?.depth, "Expected level 3")
        assertIs<XmlToken.BeginElement>(peekedToken)
        assertEquals("l3", peekedToken.name.local)
        reader.peek()
        assertEquals(3, reader.lastToken?.depth, "Expected peek to not effect level")
    }

    @Test
    fun itPeeksWithoutImpactingLastToken() {
        val payload = """
            <a>
                <b/>
                <c>
                    <d>Whee</d>
                </c>
                <e/>
                <f>
                    <g>
                        <h/>
                    </g>
                </f>
            </a>
        """.trimIndent().encodeToByteArray()
        val reader = xmlStreamReader(payload)

        assertNull(reader.lastToken, "Expected to start with null lastToken")
        assertEquals(XmlToken.BeginElement(1, "a"), reader.peek(1))
        assertNull(reader.lastToken, "Expected peek not to affect lastToken")
        assertEquals(XmlToken.BeginElement(2, "b"), reader.peek(2))
        assertEquals(XmlToken.EndElement(2, "b"), reader.peek(3))
        assertNull(reader.lastToken, "Expected peek not to affect lastToken")

        reader.nextToken()
        assertEquals(reader.lastToken, XmlToken.BeginElement(1, "a"))
        reader.nextToken()
        assertEquals(reader.lastToken, XmlToken.BeginElement(2, "b"))
        reader.nextToken()
        assertEquals(reader.lastToken, XmlToken.EndElement(2, "b"))
        reader.nextToken()
        assertEquals(reader.lastToken, XmlToken.BeginElement(2, "c"))
        reader.nextToken()
        assertEquals(reader.lastToken, XmlToken.BeginElement(3, "d"))

        assertEquals(XmlToken.Text(3, "Whee"), reader.peek(1))
        assertEquals(reader.lastToken, XmlToken.BeginElement(3, "d"), "Expected peek not to affect lastToken")
        assertEquals(XmlToken.EndElement(3, "d"), reader.peek(2))
        assertEquals(reader.lastToken, XmlToken.BeginElement(3, "d"), "Expected peek not to affect lastToken")
        assertEquals(XmlToken.EndElement(2, "c"), reader.peek(3))
        assertEquals(reader.lastToken, XmlToken.BeginElement(3, "d"), "Expected peek not to affect lastToken")
    }

    @Test
    fun itHandlesNamespaceDefaults() {
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
    fun itHandlesNamespacePrefixes() {
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
    fun itHandlesNamespacedAttributes() {
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
    fun itSubTrees() {
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
        assertIs<XmlToken.BeginElement>(token)
        assertEquals("root", token.name.local)

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
    fun itHandlesSubreadersCorrectly() {
        val payload = """
            <outermost>
                <outer-1/>
                <outer-2>
                    <inner-a>a</inner-a>
                    <inner-b>b</inner-b>
                    <inner-c>c</inner-c>
                </outer-2>
                <outer-3>
                    <inner-d>d</inner-d>
                    <inner-e>e</inner-e>
                    <inner-f>
                        <innermost>f</innermost>
                    </inner-f>
                </outer-3>
                <outer-4/>
                <outer-5>
                    <inner-g/>
                </outer-5>
            </outermost>
        """.encodeToByteArray()
        val reader = xmlStreamReader(payload)

        assertEquals(XmlToken.BeginElement(1, "outermost"), reader.nextToken())
        assertEquals(XmlToken.BeginElement(2, "outer-1"), reader.nextToken())

        var subreader = reader.subTreeReader(XmlStreamReader.SubtreeStartDepth.CHILD) // Children of <outer-1/> = ∅
        assertNull(subreader.nextToken(), "Expected no children for <outer-1/> node")

        // Special case for empty subtrees: advance lastToken to the end element
        assertEquals(XmlToken.EndElement(2, "outer-1"), reader.lastToken)

        assertEquals(XmlToken.BeginElement(2, "outer-2"), reader.nextToken())
        subreader = reader.subTreeReader((XmlStreamReader.SubtreeStartDepth.CHILD)) // 3 child tags with text
        ('a'..'c').forEach {
            assertEquals(XmlToken.BeginElement(3, "inner-$it"), subreader.peek(1))
            assertEquals(XmlToken.Text(3, "$it"), subreader.peek(2))
            assertEquals(XmlToken.EndElement(3, "inner-$it"), subreader.peek(3))

            assertEquals(XmlToken.BeginElement(3, "inner-$it"), subreader.nextToken())
            assertEquals(XmlToken.Text(3, "$it"), subreader.nextToken())
            assertEquals(XmlToken.EndElement(3, "inner-$it"), subreader.nextToken())
            assertEquals(XmlToken.EndElement(3, "inner-$it"), subreader.lastToken)
        }
        assertNull(subreader.nextToken(), "Expected no more children for <outer-2> node")

        assertEquals(XmlToken.EndElement(2, "outer-2"), reader.nextToken())
    }

    @Test
    fun itHandlesPeekingMultipleLevels() {
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

        assertIs<XmlToken.BeginElement>(rTokenPeek)
        assertEquals("r", rTokenPeek.name.local)

        assertIs<XmlToken.BeginElement>(aToken)
        assertEquals("a", aToken.name.local)

        assertIs<XmlToken.BeginElement>(rTokenTake)
        assertEquals("r", rTokenTake.name.local)

        val bToken = actual.peek(2)
        assertIs<XmlToken.BeginElement>(bToken)
        assertEquals("b", bToken.name.local)

        val aTokenTake = actual.nextToken()
        assertIs<XmlToken.BeginElement>(aTokenTake)
        assertEquals("a", aTokenTake.name.local)

        val aCloseToken = actual.peek(5) // 1:<b> 2:<c> 3:</c> 4:</b> 5:</a>
        assertIs<XmlToken.EndElement>(aCloseToken)
        assertEquals("a", aCloseToken.name.local)

        val restOfTokens = actual.allTokens()
        assertEquals(restOfTokens.size, 6)
    }

    @Test
    fun itHandlesSeekingToNodes() {
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
        assertIs<XmlToken.Text>(textNode)
        assertEquals("some text", textNode.value)

        unit = xmlStreamReader(payload)
        // match begin node of depth 2
        val l2Node = unit.seek<XmlToken.BeginElement> { it.depth == 2 }
        assertIs<XmlToken.BeginElement>(l2Node)
        assertEquals("a", l2Node.name.local)

        // verify next token is correct
        val nextNode = unit.nextToken()
        assertIs<XmlToken.BeginElement>(nextNode)
        assertEquals("b", nextNode.name.local)

        // verify no match produces null
        unit = xmlStreamReader(payload)
        val noNode = unit.seek<XmlToken.BeginElement> { it.depth == 9 }
        assertNull(noNode)
        assertNull(unit.nextToken())
    }

    @Test
    fun itThrowsErrorsOnInvalidXmlSequences() {
        val invalidTextList = listOf(
            """&lte;""",
            "&lte;",
            "&lt",
            "&#Q1234;",
            "&#3.14;",
            "&#xZZ",
            "here is a & but without an escape sequence...",
        )

        invalidTextList.forEach { testCase ->
            val input = "<a>$testCase</a>".encodeToByteArray()
            val ex = assertFailsWith<DeserializationException> {
                val actual = xmlStreamReader(input)
                actual.allTokens()
            }
            assertTrue(ex.message!!.contains("reference"), "Expected error message to contain the word 'reference'")
        }
    }

    @Test
    fun itHandlesWhitespaceCorrectly() {
        val payload = """
            <doc>
                <a> <b/> </a>
                <c>No leading/trailing spaces</c>
                <d>  Leading spaces</d>
                <e>Trailing spaces  </e>
                <f>  Leading/trailing spaces  </f>
                <g> Leading text <h/> </g> 
                <i> <j/> Trailing text </i>
                <k>   </k> <!-- Blank space only! -->
                <l> <!-- Blank space only! -->  </l>
                <m><!-- Blank space only! -->   </m>
                <n>   <!-- Blank space only! --></n>
            </doc>
        """.encodeToByteArray()
        var reader = xmlStreamReader(payload)

        fun readAndAssertEquals(vararg tokens: XmlToken) {
            tokens.forEach {
                assertEquals(it, reader.nextToken())
            }
        }

        readAndAssertEquals(XmlToken.BeginElement(1, "doc"))

        // No text nodes because they're all blank and a child <b/> tag exists
        readAndAssertEquals(
            XmlToken.BeginElement(2, "a"),
            XmlToken.BeginElement(3, "b"),
            XmlToken.EndElement(3, "b"),
            XmlToken.EndElement(2, "a"),
        )

        // A single text node
        readAndAssertEquals(
            XmlToken.BeginElement(2, "c"),
            XmlToken.Text(2, "No leading/trailing spaces"),
            XmlToken.EndElement(2, "c"),
        )

        // Text node with leading space
        readAndAssertEquals(
            XmlToken.BeginElement(2, "d"),
            XmlToken.Text(2, "  Leading spaces"),
            XmlToken.EndElement(2, "d"),
        )

        // Text node with trailing space
        readAndAssertEquals(
            XmlToken.BeginElement(2, "e"),
            XmlToken.Text(2, "Trailing spaces  "),
            XmlToken.EndElement(2, "e"),
        )

        // Text node with leading & trailing space
        readAndAssertEquals(
            XmlToken.BeginElement(2, "f"),
            XmlToken.Text(2, "  Leading/trailing spaces  "),
            XmlToken.EndElement(2, "f"),
        )

        // Text node before <h/>, no text node afterward
        readAndAssertEquals(
            XmlToken.BeginElement(2, "g"),
            XmlToken.Text(2, " Leading text "),
            XmlToken.BeginElement(3, "h"),
            XmlToken.EndElement(3, "h"),
            XmlToken.EndElement(2, "g"),
        )

        // Text node after <j/>, no text node before
        readAndAssertEquals(
            XmlToken.BeginElement(2, "i"),
            XmlToken.BeginElement(3, "j"),
            XmlToken.EndElement(3, "j"),
            XmlToken.Text(2, " Trailing text "),
            XmlToken.EndElement(2, "i"),
        )

        // Variations on blank text nodes
        ('k'..'n').map(Char::toString).forEach {
            readAndAssertEquals(
                XmlToken.BeginElement(2, it),
                XmlToken.Text(2, "   "),
                XmlToken.EndElement(2, it),
            )
        }
    }

    @Test
    fun itHandlesCdata() {
        val payload = """
            <doc>This is a <![CDATA[<test/>]]> of CDATA</doc>
        """.encodeToByteArray()
        var reader = xmlStreamReader(payload)

        reader.nextToken()
        assertEquals(XmlToken.Text(1, "This is a <test/> of CDATA"), reader.nextToken())
    }
}

fun XmlStreamReader.allTokens(): List<XmlToken> {
    val tokenList = mutableListOf<XmlToken>()
    var nextToken: XmlToken?
    do {
        nextToken = this.nextToken()
        if (nextToken != null) tokenList.add(nextToken)
    } while (nextToken != null)

    return tokenList
}
