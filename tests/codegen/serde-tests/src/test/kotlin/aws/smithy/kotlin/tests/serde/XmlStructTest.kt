/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.tests.serde

import aws.smithy.kotlin.runtime.serde.xml.XmlDeserializer
import aws.smithy.kotlin.runtime.serde.xml.XmlSerializer
import aws.smithy.kotlin.tests.serde.xml.model.FooEnum
import aws.smithy.kotlin.tests.serde.xml.model.Top
import aws.smithy.kotlin.tests.serde.xml.serde.deserializeTopDocument
import aws.smithy.kotlin.tests.serde.xml.serde.serializeTopDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class XmlStructTest {
    @Test
    fun testStructPrimitives() {
        val expected = Top {
            strField = "a string"
            enumField = FooEnum.Bar
            extra = 42
        }

        val payload = """
            <Top extra="42">
                <strField>a string</strField>
                <enumField>Bar</enumField>
            </Top>
        """.trimIndent().encodeToByteArray()

        val serializer = XmlSerializer()
        serializeTopDocument(serializer, expected)
        val actualPayload = serializer.toByteArray().decodeToString()

        val deserializer = XmlDeserializer(payload)
        val actualDeserialized = deserializeTopDocument(deserializer)
        assertEquals(expected, actualDeserialized)

        // TODO - use assertXmlStringsEqual from smithy-test
        // TODO - figure out roundtrip structure
        // TODO - turn into abstract base for XML vs JSON
    }
}
