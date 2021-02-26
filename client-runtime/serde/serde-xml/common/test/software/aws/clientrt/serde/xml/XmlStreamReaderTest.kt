/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class XmlStreamReaderTest {
    @Test
    fun itDeserializesXml() = runSuspendTest {
        val payload = """<root><x>1</x><y>2</y></root>""".trimIndent().encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        val expected = listOf(
            XmlToken.BeginElement("root"),
            XmlToken.BeginElement("x"),
            XmlToken.Text("1"),
            XmlToken.EndElement("x"),
            XmlToken.BeginElement("y"),
            XmlToken.Text("2"),
            XmlToken.EndElement("y"),
            XmlToken.EndElement("root"),
            XmlToken.EndDocument
        )
        assertEquals(expected, actual)
    }

    @Test
    fun itDeserializesXmlWithAttributes() = runSuspendTest {
        val payload = """<batch><add id="tt0484562"><field name="title">The Seeker: The Dark Is Rising</field></add><delete id="tt0301199" /></batch>""".trimIndent().encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        val expected = listOf(
            XmlToken.BeginElement("batch"),
            XmlToken.BeginElement("add", mapOf(XmlToken.QualifiedName("id") to "tt0484562")),
            XmlToken.BeginElement("field", mapOf(XmlToken.QualifiedName("name") to "title")),
            XmlToken.Text("The Seeker: The Dark Is Rising"),
            XmlToken.EndElement("field"),
            XmlToken.EndElement("add"),
            XmlToken.BeginElement("delete", mapOf(XmlToken.QualifiedName("id") to "tt0301199")),
            XmlToken.EndElement("delete"),
            XmlToken.EndElement("batch"),
            XmlToken.EndDocument
        )

        assertEquals(expected, actual)
    }

    @Test
    fun garbageInGarbageOut() = runSuspendTest {
        val payload = """you try to parse me once, jokes on me..try twice jokes on you bucko.""".trimIndent().encodeToByteArray()
        assertFailsWith(XmlGenerationException::class) { xmlStreamReader(payload).allTokens() }
    }

    @Test
    fun itHandlesNilNodeValues() = runSuspendTest {
        val payload = """<null></null>""".encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()
        val expected = listOf(
            XmlToken.BeginElement("null"),
            XmlToken.EndElement("null"),
            XmlToken.EndDocument
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
            XmlToken.BeginElement("root"),
            XmlToken.BeginElement("num"),
            XmlToken.Text("1"),
            XmlToken.EndElement("num"),
            XmlToken.BeginElement("str"),
            XmlToken.Text("string"),
            XmlToken.EndElement("str"),
            XmlToken.BeginElement("list"),
            XmlToken.BeginElement("value"),
            XmlToken.Text("1"),
            XmlToken.EndElement("value"),
            XmlToken.BeginElement("value"),
            XmlToken.Text("2.3456"),
            XmlToken.EndElement("value"),
            XmlToken.BeginElement("value"),
            XmlToken.Text("3"),
            XmlToken.EndElement("value"),
            XmlToken.EndElement("list"),
            XmlToken.BeginElement("nested"),
            XmlToken.BeginElement("l2"),
            XmlToken.BeginElement("list"),
            XmlToken.BeginElement("x"),
            XmlToken.Text("x"),
            XmlToken.EndElement("x"),
            XmlToken.BeginElement("value"),
            XmlToken.Text("true"),
            XmlToken.EndElement("value"),
            XmlToken.EndElement("list"),
            XmlToken.EndElement("l2"),
            XmlToken.BeginElement("falsey"),
            XmlToken.Text("false"),
            XmlToken.EndElement("falsey"),
            XmlToken.EndElement("nested"),
            XmlToken.BeginElement("null"),
            XmlToken.EndElement("null"),
            XmlToken.EndElement("root"),
            XmlToken.EndDocument
        )

        assertEquals(expected, actual)
    }

    @Test
    fun itSkipsValuesRecursively() = runSuspendTest {
        val payload = """
            <payload><x>1></x><unknown><a>a</a><b>b</b><c><list><element>d</element><element>e</element><element>f</element></list></c><g><h>h</h><i>i</i></g></unknown><y>2></y></payload>
        """.trimIndent().encodeToByteArray()
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

        assertEquals("unknown", nt.qualifiedName.name)
        reader.skipNext()

        val y = reader.nextToken() as XmlToken.BeginElement
        assertEquals("y", y.qualifiedName.name)
    }

    @Test
    fun itSkipsSimpleValues() = runSuspendTest {
        val payload = """<payload><x>1</x><z>unknown</z><y>2</y></payload>""".trimIndent().encodeToByteArray()
        val reader = xmlStreamReader(payload)
        // skip x
        reader.apply {
            nextToken() // begin obj
            nextToken() // x
        }
        reader.skipNext()

        assertTrue(reader.peek() is XmlToken.BeginElement)

        val zElement = reader.nextToken() as XmlToken.BeginElement
        assertEquals("z", zElement.qualifiedName.name)
        reader.skipNext()

        val yElement = reader.nextToken() as XmlToken.BeginElement
        assertEquals("y", yElement.qualifiedName.name)
    }

    @Test
    fun itPeeksWithoutImpactingNestingLevel() = runSuspendTest {
        val payload = """<l1><l2><l3>text</l3></l2></l1>""".trimIndent().encodeToByteArray()
        val reader = xmlStreamReader(payload)

        assertTrue(reader.currentDepth == 0, "Expected to start at level 0")
        var peekedToken = reader.peek()
        assertTrue(peekedToken is XmlToken.BeginElement)
        assertTrue(peekedToken.qualifiedName.name == "l1")
        assertTrue(reader.currentDepth == 0, "Expected peek to not effect level")

        peekedToken = reader.nextToken()
        assertTrue(reader.currentDepth == 1, "Expected level 1")
        assertTrue(peekedToken is XmlToken.BeginElement)
        assertTrue(peekedToken.qualifiedName.name == "l1")
        reader.peek()
        assertTrue(reader.currentDepth == 1, "Expected peek to not effect level")

        peekedToken = reader.nextToken()
        assertTrue(reader.currentDepth == 2, "Expected level 2")
        assertTrue(peekedToken is XmlToken.BeginElement)
        assertTrue(peekedToken.qualifiedName.name == "l2")
        reader.peek()
        assertTrue(reader.currentDepth == 2, "Expected peek to not effect level")
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
            XmlToken.BeginElement(XmlToken.QualifiedName("MyStructure", namespaceUri = "http://foo.com"), nsDeclarations = listOf(XmlToken.Namespace("http://foo.com"))),
            XmlToken.BeginElement(XmlToken.QualifiedName("foo", namespaceUri = "http://foo.com")),
            XmlToken.Text("bar"),
            XmlToken.EndElement(XmlToken.QualifiedName("foo", namespaceUri = "http://foo.com")),
            XmlToken.EndElement(XmlToken.QualifiedName("MyStructure", namespaceUri = "http://foo.com")),
            XmlToken.EndDocument
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
            XmlToken.BeginElement(XmlToken.QualifiedName("MyStructure"), nsDeclarations = listOf(XmlToken.Namespace("http://foo.com", "baz"))),
            XmlToken.BeginElement("foo"),
            XmlToken.Text("what"),
            XmlToken.EndElement("foo"),
            XmlToken.BeginElement(XmlToken.QualifiedName("bar", namespaceUri = "http://foo.com", namespacePrefix = "baz")),
            XmlToken.Text("yeah"),
            XmlToken.EndElement(XmlToken.QualifiedName("bar", namespaceUri = "http://foo.com", namespacePrefix = "baz")),
            XmlToken.EndElement("MyStructure"),
            XmlToken.EndDocument
        )

        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesNamespacedAttriburtes() = runSuspendTest {
        val payload = """
            <MyStructure xmlns:baz="http://foo.com">
                <foo baz:k1="v1"></foo>
            </MyStructure>
        """.encodeToByteArray()
        val actual = xmlStreamReader(payload).allTokens()

        val expected = listOf(
            XmlToken.BeginElement(XmlToken.QualifiedName("MyStructure"), nsDeclarations = listOf(XmlToken.Namespace("http://foo.com", "baz"))),
            XmlToken.BeginElement("foo", attributes = mapOf(XmlToken.QualifiedName("k1", "http://foo.com", "baz") to "v1")),
            XmlToken.EndElement("foo"),
            XmlToken.EndElement("MyStructure"),
            XmlToken.EndDocument
        )

        assertEquals(expected, actual)
    }
}

suspend fun XmlStreamReader.allTokens(): List<XmlToken> {
    val tokens = mutableListOf<XmlToken>()
    while (true) {
        val token = nextToken()
        tokens.add(token)
        if (token is XmlToken.EndDocument) {
            break
        }
    }
    return tokens
}
