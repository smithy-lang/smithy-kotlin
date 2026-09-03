/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema.trait

import aws.smithy.kotlin.runtime.time.TimestampFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class SerdeTraitsTest {
    @Test
    fun testTraitIds() {
        assertEquals("smithy.api#jsonName", SerdeTraits.JsonNameTrait.ID.absoluteId)
        assertEquals("smithy.api#xmlName", SerdeTraits.XmlNameTrait.ID.absoluteId)
        assertEquals("smithy.api#timestampFormat", SerdeTraits.TimestampFormatTrait.ID.absoluteId)
        assertEquals("smithy.api#required", SerdeTraits.RequiredTrait.ID.absoluteId)
        assertEquals("smithy.api#sparse", SerdeTraits.SparseTrait.ID.absoluteId)
    }

    @Test
    fun testInstanceIdMatchesCompanion() {
        assertEquals(SerdeTraits.JsonNameTrait.ID, SerdeTraits.JsonNameTrait("x").id)
        assertEquals(SerdeTraits.XmlNameTrait.ID, SerdeTraits.XmlNameTrait("x").id)
        assertEquals(SerdeTraits.TimestampFormatTrait.ID, SerdeTraits.TimestampFormatTrait(TimestampFormat.ISO_8601).id)
        assertEquals(SerdeTraits.RequiredTrait.ID, SerdeTraits.RequiredTrait.id)
        assertEquals(SerdeTraits.SparseTrait.ID, SerdeTraits.SparseTrait.id)
    }

    @Test
    fun testTraitValues() {
        assertEquals("bird_name", SerdeTraits.JsonNameTrait("bird_name").value)
        assertEquals("Bird", SerdeTraits.XmlNameTrait("Bird").value)
        assertEquals(TimestampFormat.EPOCH_SECONDS, SerdeTraits.TimestampFormatTrait(TimestampFormat.EPOCH_SECONDS).format)
    }

    @Test
    fun testToString() {
        assertEquals("JsonName(bird_name)", SerdeTraits.JsonNameTrait("bird_name").toString())
        assertEquals("XmlName(Bird)", SerdeTraits.XmlNameTrait("Bird").toString())
        assertEquals("TimestampFormat(ISO_8601)", SerdeTraits.TimestampFormatTrait(TimestampFormat.ISO_8601).toString())
        assertEquals("Required", SerdeTraits.RequiredTrait.toString())
        assertEquals("Sparse", SerdeTraits.SparseTrait.toString())
    }

    /** Each trait hardcodes its shape id and `toString` independently, so pin every one of them. */
    @Test
    fun testEveryValuelessTraitIdAndToString() {
        val expected = listOf(
            Triple(SerdeTraits.XmlAttributeTrait, "smithy.api#xmlAttribute", "XmlAttribute"),
            Triple(SerdeTraits.XmlFlattenedTrait, "smithy.api#xmlFlattened", "XmlFlattened"),
            Triple(SerdeTraits.RequiredTrait, "smithy.api#required", "Required"),
            Triple(SerdeTraits.SparseTrait, "smithy.api#sparse", "Sparse"),
            Triple(SerdeTraits.SensitiveTrait, "smithy.api#sensitive", "Sensitive"),
            Triple(SerdeTraits.IdempotencyTokenTrait, "smithy.api#idempotencyToken", "IdempotencyToken"),
            Triple(SerdeTraits.StreamingTrait, "smithy.api#streaming", "Streaming"),
            Triple(SerdeTraits.RequiresLengthTrait, "smithy.api#requiresLength", "RequiresLength"),
            Triple(SerdeTraits.EventHeaderTrait, "smithy.api#eventHeader", "EventHeader"),
            Triple(SerdeTraits.EventPayloadTrait, "smithy.api#eventPayload", "EventPayload"),
            Triple(SerdeTraits.HttpLabelTrait, "smithy.api#httpLabel", "HttpLabel"),
            Triple(SerdeTraits.HttpPayloadTrait, "smithy.api#httpPayload", "HttpPayload"),
            Triple(SerdeTraits.HttpQueryParamsTrait, "smithy.api#httpQueryParams", "HttpQueryParams"),
            Triple(SerdeTraits.HttpResponseCodeTrait, "smithy.api#httpResponseCode", "HttpResponseCode"),
            Triple(SerdeTraits.HostLabelTrait, "smithy.api#hostLabel", "HostLabel"),
        )

        expected.forEach { (trait, absoluteId, rendered) ->
            assertEquals(absoluteId, trait.id.absoluteId)
            assertEquals(rendered, trait.toString())
        }
    }

    @Test
    fun testEveryValueTraitIdAndToString() {
        val expected = listOf(
            Triple(SerdeTraits.JsonNameTrait("v"), "smithy.api#jsonName", "JsonName(v)"),
            Triple(SerdeTraits.XmlNameTrait("v"), "smithy.api#xmlName", "XmlName(v)"),
            Triple(SerdeTraits.XmlNamespaceTrait("uri"), "smithy.api#xmlNamespace", "XmlNamespace(uri)"),
            Triple(SerdeTraits.XmlNamespaceTrait("uri", "p"), "smithy.api#xmlNamespace", "XmlNamespace(uri, p)"),
            Triple(SerdeTraits.MediaTypeTrait("text/plain"), "smithy.api#mediaType", "MediaType(text/plain)"),
            Triple(
                SerdeTraits.TimestampFormatTrait(TimestampFormat.EPOCH_SECONDS),
                "smithy.api#timestampFormat",
                "TimestampFormat(EPOCH_SECONDS)",
            ),
            Triple(
                SerdeTraits.AwsQueryErrorTrait("Throttled", 429),
                "aws.protocols#awsQueryError",
                "AwsQueryError(Throttled, 429)",
            ),
            Triple(SerdeTraits.HttpHeaderTrait("x-a"), "smithy.api#httpHeader", "HttpHeader(x-a)"),
            Triple(SerdeTraits.HttpPrefixHeadersTrait("x-"), "smithy.api#httpPrefixHeaders", "HttpPrefixHeaders(x-)"),
            Triple(SerdeTraits.HttpQueryTrait("q"), "smithy.api#httpQuery", "HttpQuery(q)"),
            Triple(SerdeTraits.ContextParamTrait("Region"), "smithy.rules#contextParam", "ContextParam(Region)"),
        )

        expected.forEach { (trait, absoluteId, rendered) ->
            assertEquals(absoluteId, trait.id.absoluteId)
            assertEquals(rendered, trait.toString())
        }
    }
}
