/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.xml

import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class XmlStreamWriterTest {

    @Test
    fun checkXmlSerializesCorrectlyWithWrapper() {
        val msg1 = Message(
            912345678901,
            "How do I stream XML in Java?",
            null,
            user1,
        )
        val msg2 = Message(
            912345678902,
            "@xml_newb just use XmlWriter!",
            arrayOf(50.454722, -104.606667),
            user2,
        )
        assertEquals(
            expected,
            writeXmlStream(
                listOf(msg1, msg2),
            )?.decodeToString(),
        )
    }

    @Test
    fun checkCloseIsIdempotent() {
        val writer = generateSimpleDocument()
        assertEquals(expectedIdempotent, writer.bytes.decodeToString())
        assertEquals(expectedIdempotent, writer.bytes.decodeToString())
    }

    private fun generateSimpleDocument() = xmlStreamWriter().apply {
        startDocument()
        startTag("id")
        text(912345678901.toString())
        endTag("id")
        endDocument()
    }

    @Test
    fun checkNonHumanReadable() {
        val writer = generateSimpleDocument()
        assertEquals(expectedNoIndent, writer.bytes.decodeToString())
    }

    @Test
    fun itWritesXMLWithAttributes() {
        val writer = xmlStreamWriter(pretty = false)

        writer.startTag("batch")
        writer.startTag("add").attribute("id", "tt0484562")
        writer.startTag("field").attribute("name", "title")
        writer.text("The Seeker: The Dark Is Rising")
        writer.endTag("field")
        writer.endTag("add")
        writer.startTag("delete").attribute("id", "tt0301199")
        writer.endTag("delete")
        writer.endTag("batch")

        // adapted from https://docs.aws.amazon.com/cloudsearch/latest/developerguide/documents-batch-xml.html
        val expected = """<batch><add id="tt0484562"><field name="title">The Seeker: The Dark Is Rising</field></add><delete id="tt0301199"/></batch>"""

        assertEquals(expected, writer.text)
    }

    // The following escape tests were adapted from
    // https://github.com/awslabs/smithy-rs/blob/c15289a7163cb6344b088a0ee39244df2967070a/rust-runtime/smithy-xml/src/unescape.rs
    @Test
    fun itHandlesEscaping() {
        val testCases = mapOf(
            "< > ' \" &" to """<a>&lt; &gt; ' " &amp;</a>""",
            """hello 🍕!""" to """<a>hello 🍕!</a>""",
            """a<b>c\"d'e&f;;""" to """<a>a&lt;b&gt;c\"d'e&amp;f;;</a>""",
            "\n" to """<a>&#xA;</a>""",
            "\r" to """<a>&#xD;</a>""",
        )

        testCases.forEach { (input, expected) ->
            val writer = xmlStreamWriter(pretty = false)

            writer.startTag("a")
            writer.text(input)
            writer.endTag("a")

            assertEquals(expected, writer.text)
        }
    }

    @Test
    fun itHandlesNonAsciiCharacters() {
        val tag = "textTest"
        val payload = (0..1023).map(Int::toChar).joinToString("")

        val writer = xmlStreamWriter()
        writer.startTag(tag)
        writer.text(payload)
        writer.endTag(tag)
        val serialized = writer.bytes

        val reader = xmlStreamReader(serialized)
        reader.nextToken() // opening tag
        val textToken = reader.nextToken() as XmlToken.Text
        assertEquals(payload, textToken.value)
    }

    /**
     * The set of EOL characters and their corresponding escaped form are:
     *
     * | Name| Unicode code point | Escape Sequences |
     * |-----|-------------|-----------------|
     * | `CiAK` | `'\n \n'` | `'&#xA; &#xA;'` |
     * | `YQ0KIGIKIGMN` | `'a\r\n b\n c\r'` | `'a&#xD;&#xA; b&#xA; c&#xD;'` |
     * | `YQ3ChSBiwoU=` | `'a\r\u0085 b\u0085'` | `'a&#xD;&#x85; b&#x85;'` |
     * | `YQ3igKggYsKFIGPigKg=` | `'a\r\u2028 b\u0085 c\u2028'` | `'a&#xD;&#x2028; b&#x85; c&#x2028;'` |
     */
    @Test
    fun itEncodesEndOfLine() {
        val testCaseMap = mapOf(
            "\n \n" to """<a>&#xA; &#xA;</a>""",
            "a\r\n b\n c\r" to """<a>a&#xD;&#xA; b&#xA; c&#xD;</a>""",
            "a\r\u0085 b\u0085" to """<a>a&#xD;&#x85; b&#x85;</a>""",
            "a\r\u2028 b\u0085 c\u2028" to """<a>a&#xD;&#x2028; b&#x85; c&#x2028;</a>""",
        )

        testCaseMap.forEach { (input, expected) ->
            val writer = xmlStreamWriter(pretty = false)

            writer.startTag("a")
            writer.text(input)
            writer.endTag("a")

            assertEquals(expected, writer.text)
        }
    }
}

const val expectedIdempotent = """<?xml version="1.0"?><id>912345678901</id>"""

const val expectedNoIndent = """<?xml version="1.0"?><id>912345678901</id>"""

fun writeXmlStream(messages: List<Message>): ByteArray? {
    val writer = xmlStreamWriter(true)
    return writeMessagesArray(writer, messages)
}

fun writeMessagesArray(writer: XmlStreamWriter, messages: List<Message>): ByteArray? {
    writer.apply {
        startTag("messages")
    }
    for (message in messages) {
        writeMessage(writer, message)
    }
    writer.endTag("messages")
    return writer.bytes
}

fun writeMessage(writer: XmlStreamWriter, message: Message) {
    writer.apply {
        startTag("message")
        startTag("id")
        text(message.id)
        endTag("id")
        startTag("text")
        text(message.text)
        endTag("text")

        if (message.geo != null) {
            writeDoublesArray(writer, message.geo)
        } else {
            writer.startTag("geo")
            writer.endTag("geo")
        }
    }
    writeUser(writer, message.user)
    writer.endTag("message")
}

fun writeUser(writer: XmlStreamWriter, user: User) {
    writer.startTag("user")
    writer.startTag("name")
    writer.text(user.name)
    writer.endTag("name")
    writer.startTag("followers_count")
    writer.text(user.followersCount)
    writer.endTag("followers_count")
    writer.endTag("user")
}

fun writeDoublesArray(writer: XmlStreamWriter, doubles: Array<Double>?) {
    writer.startTag("geo")
    if (doubles != null) {
        for (value in doubles) {
            writer.startTag("position")
            writer.text(value)
            writer.endTag("position")
        }
    }
    writer.endTag("geo")
}

val user1 = User("xml_newb", 41)
val user2 = User("jesse", 2)

class Message(val id: Long, val text: String, val geo: Array<Double>?, val user: User)
data class User(val name: String, val followersCount: Int)

val expected: String = """
    <messages>
        <message>
            <id>912345678901</id>
            <text>How do I stream XML in Java?</text>
            <geo />
            <user>
                <name>xml_newb</name>
                <followers_count>41</followers_count>
            </user>
        </message>
        <message>
            <id>912345678902</id>
            <text>@xml_newb just use XmlWriter!</text>
            <geo>
                <position>50.454722</position>
                <position>-104.606667</position>
            </geo>
            <user>
                <name>jesse</name>
                <followers_count>2</followers_count>
            </user>
        </message>
    </messages>
    
""".trimIndent()
