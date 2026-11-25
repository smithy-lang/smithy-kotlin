/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client.endpoints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EndpointUrlTest {
    @Test
    fun testWithoutQuery() {
        assertEquals("https://example.com", withoutQuery("https://example.com?foo=bar"))
        assertEquals("https://example.com/a%20b", withoutQuery("https://example.com/a%20b?foo=bar&baz=qux"))
        assertEquals("https://example.com", withoutQuery("https://example.com?"))
    }

    @Test
    fun testWithoutQueryUnchanged() {
        assertNull(withoutQuery(null))
        assertEquals("https://example.com/path", withoutQuery("https://example.com/path"))
        assertEquals("https://example.com#a?b", withoutQuery("https://example.com#a?b"))
        assertEquals("invalidscheme://host?foo=bar", withoutQuery("invalidscheme://host?foo=bar"))
    }
}
