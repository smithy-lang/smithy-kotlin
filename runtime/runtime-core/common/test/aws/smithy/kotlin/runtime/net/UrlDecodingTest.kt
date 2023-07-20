/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlDecodingTest {
    @Test
    fun testDecodeAll() {
        assertTrue(UrlDecoding.DecodePath in UrlDecoding.DecodeAll)
        assertTrue(UrlDecoding.DecodeQueryParameters in UrlDecoding.DecodeAll)
        assertTrue(UrlDecoding.DecodeFragment in UrlDecoding.DecodeAll)
        assertFalse(UrlDecoding.DecodeNone in UrlDecoding.DecodeAll)
    }

    @Test
    fun testPlus() {
        val test = UrlDecoding.DecodePath + UrlDecoding.DecodeFragment
        assertTrue(UrlDecoding.DecodePath in test)
        assertFalse(UrlDecoding.DecodeQueryParameters in test)
        assertTrue(UrlDecoding.DecodeFragment in test)
    }

    @Test
    fun testMinus() {
        val test = UrlDecoding.DecodeAll - UrlDecoding.DecodeFragment
        assertTrue(UrlDecoding.DecodePath in test)
        assertTrue(UrlDecoding.DecodeQueryParameters in test)
        assertFalse(UrlDecoding.DecodeFragment in test)
    }
}
