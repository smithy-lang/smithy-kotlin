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
package software.aws.clientrt.serde.json

import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class JsonStreamWriterTest {

    @Test
    fun `check json serializes correctly with wrapper`() {
        val msg1 = Message(
            912345678901,
            "How do I stream JSON in Java?",
            null,
            user1
        )
        val msg2 = Message(
            912345678902,
            "@json_newb just use JsonWriter!",
            arrayOf(50.454722, -104.606667),
            user2
        )
        assertEquals(
            expected, writeJsonStream(
                listOf(msg1, msg2)
            )?.decodeToString())
    }

    @Test
    fun `check close is idempotent`() {
        val writer = jsonStreamWriter(true)
        writer.beginObject()
        writer.writeName("id")
        writer.writeValue(912345678901)
        writer.endObject()
        assertEquals(expectedIdempotent, writer.bytes?.decodeToString())
        assertEquals(expectedIdempotent, writer.bytes?.decodeToString())
    }

    @Test
    fun `check non human readable`() {
        val writer = jsonStreamWriter()
        writer.beginObject()
        writer.writeName("id")
        writer.writeValue(912345678901)
        writer.endObject()
        assertEquals(expectedNoIndent, writer.bytes?.decodeToString())
    }
}

val expectedIdempotent = """{
    "id": 912345678901
}"""

val expectedNoIndent = """{"id":912345678901}"""

fun writeJsonStream(messages: List<Message>): ByteArray? {
    val writer = jsonStreamWriter(true)
    return writeMessagesArray(writer, messages)
}

fun writeMessagesArray(writer: JsonStreamWriter, messages: List<Message>): ByteArray? {
    writer.beginArray()
    for (message in messages) {
        writeMessage(writer, message)
    }
    writer.endArray()
    return writer.bytes
}

fun writeMessage(writer: JsonStreamWriter, message: Message) {
    writer.beginObject()
    writer.writeName("id")
    writer.writeValue(message.id)
    writer.writeName("text")
    writer.writeValue(message.text)
    if (message.geo != null) {
        writer.writeName("geo")
        writeDoublesArray(writer, message.geo)
    } else {
        writer.writeName("geo")
        writer.writeNull()
    }
    writer.writeName("user")
    writeUser(writer, message.user)
    writer.endObject()
}

fun writeUser(writer: JsonStreamWriter, user: User) {
    writer.beginObject()
    writer.writeName("name")
    writer.writeValue(user.name)
    writer.writeName("followers_count")
    writer.writeValue(user.followersCount)
    writer.endObject()
}

fun writeDoublesArray(writer: JsonStreamWriter, doubles: Array<Double>?) {
    writer.beginArray()
    if (doubles != null) {
        for (value in doubles) {
            writer.writeValue(value)
        }
    }
    writer.endArray()
}

val user1 = User("json_newb", 41)
val user2 = User("jesse", 2)

class Message(val id: Long, val text: String, val geo: Array<Double>?, val user: User)
data class User(val name: String, val followersCount: Int)

val expected: String = """[
    {
        "id": 912345678901,
        "text": "How do I stream JSON in Java?",
        "user": {
            "name": "json_newb",
            "followers_count": 41
        }
    },
    {
        "id": 912345678902,
        "text": "@json_newb just use JsonWriter!",
        "geo": [
            50.454722,
            -104.606667
        ],
        "user": {
            "name": "jesse",
            "followers_count": 2
        }
    }
]"""
