/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package smithy.kotlin.nullability

import kotlin.test.*

class ErrorCorrectionTest {
    @Test
    fun testErrorCorrectionClientMode() {
        val actual = smithy.kotlin.nullability.client.model.TestStruct.Builder()
            .apply {
                strValue = "abcd"
                correctErrors()
            }.build()

        assertEquals("abcd", actual.strValue)

        // don't default non-required fields or clientOptional
        assertNull(actual.notRequired)
        assertNull(actual.clientOptionalValue)

        // check nullability of generated type
        assertNotNull(actual.mapValue)
        assertNotNull(actual.listValue)
        assertNotNull(actual.nestedListValue)
        assertNotNull(actual.mapValue)
        assertNotNull(actual.nested)

        // check defaults were set/corrected
        assertContentEquals(ByteArray(0), actual.blob)
        assertEquals(emptyList(), actual.listValue)
        assertEquals(emptyList(), actual.nestedListValue)
        assertEquals(emptyMap(), actual.mapValue)

        // error correction should apply recursively
        assertEquals("", actual.nested.a)

        // enums and unions become unknown variants
        assertEquals(smithy.kotlin.nullability.client.model.Enum.SdkUnknown("no value provided"), actual.enum)
        assertEquals(smithy.kotlin.nullability.client.model.U.SdkUnknown, actual.union)
    }

    @Test
    fun testErrorCorrectionClientCarefulMode() {
        val actual = smithy.kotlin.nullability.clientcareful.model.TestStruct.Builder()
            .apply {
                strValue = "abcd"
                correctErrors()
            }.build()

        assertEquals("abcd", actual.strValue)

        // don't default non-required fields or clientOptional
        assertNull(actual.notRequired)
        assertNull(actual.clientOptionalValue)

        // check nullability of generated type
        assertNotNull(actual.mapValue)
        assertNotNull(actual.listValue)
        assertNotNull(actual.nestedListValue)
        assertNotNull(actual.mapValue)

        // set defaults for everything else
        assertContentEquals(ByteArray(0), actual.blob)
        assertEquals(emptyList(), actual.listValue)
        assertEquals(emptyList(), actual.nestedListValue)
        assertEquals(emptyMap(), actual.mapValue)

        // nested struct and union values should be null for client careful mode
        assertNull(actual.nested)
        assertNull(actual.union)

        // enums become unknown variant
        assertEquals(smithy.kotlin.nullability.clientcareful.model.Enum.SdkUnknown("no value provided"), actual.enum)
    }
}
