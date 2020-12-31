/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

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
            user1
        )
        val msg2 = Message(
            912345678902,
            "@xml_newb just use XmlWriter!",
            arrayOf(50.454722, -104.606667),
            user2
        )
        assertEquals(
            expected,
            writeXmlStream(
                listOf(msg1, msg2)
            )?.decodeToString()
        )
    }

    @Test
    fun checkCloseIsIdempotent() {
        val writer = generateSimpleDocument()
        assertEquals(expectedIdempotent, writer.bytes.decodeToString())
        assertEquals(expectedIdempotent, writer.bytes.decodeToString())
    }

    private fun generateSimpleDocument() = xmlStreamWriter(true).apply {
        startDocument(null, null)
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
        val expected = """<batch><add id="tt0484562"><field name="title">The Seeker: The Dark Is Rising</field></add><delete id="tt0301199" /></batch>"""

        assertEquals(expected, writer.toString())
    }

    @Test
    fun itCanHandleEscapingCharacters() {
        val writer = xmlStreamWriter(pretty = false)
        writer.startTag("hello")
        writer.attribute("key", """<'"&>""")
        writer.text("""<'"&>""")
        writer.endTag("hello")

        val actual = writer.toString()

        val bestPractise = """<hello key="&lt;&apos;&quot;&amp;&gt;">&lt;&apos;&quot;&amp;&gt;</hello>"""
        try {
            assertEquals(bestPractise, actual)
        } catch (e: AssertionError) {
            val minimumEscapingToSpec = """<hello key="&lt;'&quot;&amp;>">&lt;'"&amp;></hello>"""
            assertEquals(minimumEscapingToSpec, actual)
        }
    }

    @Test
    fun canHandleExplicitNamespacesWithSetPrefixes() {
        val writer = xmlStreamWriter(pretty = false)
        writer.setPrefix("", "http://default.com")
        writer.setPrefix("ex", "http://example.com")
        writer.startTag("hello", "http://example.com")
        writer.attribute("name", "Julia", "http://example.com")
        writer.setPrefix("ex2", "http://second.com") //nested namespace
        writer.startTag("world", "http://example.com")
        writer.attribute("nested", "value", "http://second.com")
        writer.endTag("world", "http://example.com")
        writer.endTag("hello", "http://example.com")
        writer.endDocument()

        val expected = """<ex:hello ex:name="Julia" xmlns="http://default.com" xmlns:ex="http://example.com"><ex:world ex2:nested="value" xmlns:ex2="http://second.com" /></ex:hello>"""
        assertEquals(expected, writer.toString())
    }

    @Test
    fun canHandleImplicitNamespacesWithGeneratedPrefixes() {
        val writer = xmlStreamWriter(pretty = false)
        writer.startTag("hello", "http://example.com")
        writer.attribute("name", "Julia", "http://example.com")
        writer.startTag("world", "")
        writer.endTag("world", "")
        writer.endTag("hello", "http://example.com")
        writer.endDocument()

        val expected = """<n1:hello n1:name="Julia" xmlns:n1="http://example.com"><world /></n1:hello>"""
        assertEquals(expected, writer.toString())
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
        attribute("id", message.id.toString())
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
    <message id="912345678901">
        <text>How do I stream XML in Java?</text>
        <geo />
        <user>
            <name>xml_newb</name>
            <followers_count>41</followers_count>
        </user>
    </message>
    <message id="912345678902">
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
