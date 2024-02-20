/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.tests.serde

import aws.smithy.kotlin.runtime.serde.xml.XmlDeserializer
import aws.smithy.kotlin.runtime.serde.xml.XmlSerializer
import aws.smithy.kotlin.runtime.smithy.test.assertXmlStringsEqual
import kotlin.test.assertEquals

abstract class AbstractXmlTest {
    fun <T> testRoundTrip(
        expected: T,
        payload: String,
        serializerFn: (XmlSerializer, T) -> Unit,
        deserializerFn: (XmlDeserializer) -> T,
    ) {
        val serializer = XmlSerializer()
        serializerFn(serializer, expected)
        val actualPayload = serializer.toByteArray().decodeToString()
        assertXmlStringsEqual(payload, actualPayload)

        val deserializer = XmlDeserializer(payload.encodeToByteArray())
        val actualDeserialized = deserializerFn(deserializer)
        assertEquals(expected, actualDeserialized)
    }
}
