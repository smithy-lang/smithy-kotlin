/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema.trait

import aws.smithy.kotlin.runtime.time.TimestampFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class TraitTest {
    @Test
    fun testTraitIds() {
        assertEquals("smithy.api#jsonName", JsonNameTrait.ID.absoluteId)
        assertEquals("smithy.api#xmlName", XmlNameTrait.ID.absoluteId)
        assertEquals("smithy.api#timestampFormat", TimestampFormatTrait.ID.absoluteId)
        assertEquals("smithy.api#required", RequiredTrait.ID.absoluteId)
        assertEquals("smithy.api#sparse", SparseTrait.ID.absoluteId)
    }

    @Test
    fun testInstanceIdMatchesCompanion() {
        assertEquals(JsonNameTrait.ID, JsonNameTrait("x").id)
        assertEquals(XmlNameTrait.ID, XmlNameTrait("x").id)
        assertEquals(TimestampFormatTrait.ID, TimestampFormatTrait(TimestampFormat.ISO_8601).id)
        assertEquals(RequiredTrait.ID, RequiredTrait.id)
        assertEquals(SparseTrait.ID, SparseTrait.id)
    }

    @Test
    fun testTraitValues() {
        assertEquals("bird_name", JsonNameTrait("bird_name").value)
        assertEquals("Bird", XmlNameTrait("Bird").value)
        assertEquals(TimestampFormat.EPOCH_SECONDS, TimestampFormatTrait(TimestampFormat.EPOCH_SECONDS).format)
    }

    @Test
    fun testToString() {
        assertEquals("JsonName(bird_name)", JsonNameTrait("bird_name").toString())
        assertEquals("XmlName(Bird)", XmlNameTrait("Bird").toString())
        assertEquals("TimestampFormat(ISO_8601)", TimestampFormatTrait(TimestampFormat.ISO_8601).toString())
        assertEquals("Required", RequiredTrait.toString())
        assertEquals("Sparse", SparseTrait.toString())
    }
}
