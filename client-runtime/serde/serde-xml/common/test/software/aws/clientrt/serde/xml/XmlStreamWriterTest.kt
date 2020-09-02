/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.serde.xml

import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class XmlStreamWriterTest {

    @Test
    fun `check xml serializes correctly with wrapper`() {
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
            expected, writeXmlStream(
                listOf(msg1, msg2)
            )?.decodeToString())
    }

    @Test
    fun `check close is idempotent`() {
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
    fun `check non human readable`() {
        val writer = generateSimpleDocument()
        assertEquals(expectedNoIndent, writer.bytes.decodeToString())
    }

    @Test
    fun `it writes XML with attributes`() {
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
</messages>""".trimIndent()
