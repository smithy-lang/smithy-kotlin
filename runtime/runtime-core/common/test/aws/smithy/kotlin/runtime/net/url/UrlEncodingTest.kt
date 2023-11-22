/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlEncodingTest {
    @Test
    fun testDecodeAll() {
        assertTrue(UrlEncoding.Path in UrlEncoding.All)
        assertTrue(UrlEncoding.QueryParameters in UrlEncoding.All)
        assertTrue(UrlEncoding.Fragment in UrlEncoding.All)
        assertFalse(UrlEncoding.None in UrlEncoding.All)
    }

    @Test
    fun testPlus() {
        val test = UrlEncoding.Path + UrlEncoding.Fragment
        assertTrue(UrlEncoding.Path in test)
        assertFalse(UrlEncoding.QueryParameters in test)
        assertTrue(UrlEncoding.Fragment in test)
    }

    @Test
    fun testMinus() {
        val test = UrlEncoding.All - UrlEncoding.Fragment
        assertTrue(UrlEncoding.Path in test)
        assertTrue(UrlEncoding.QueryParameters in test)
        assertFalse(UrlEncoding.Fragment in test)
    }
}
