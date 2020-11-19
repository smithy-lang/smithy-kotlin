/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.util

import kotlin.test.*

class AttributesTest {
    @Test
    fun testPropertyBag() {
        val strKey = AttributeKey<String>("string")
        val intKey = AttributeKey<Int>("int")
        val attributes = Attributes()

        attributes[strKey] = "foo"
        assertTrue(attributes.contains(strKey))
        assertEquals("foo", attributes[strKey])

        assertEquals("foo", attributes.take(strKey))
        assertFalse(attributes.contains(strKey))

        assertNull(attributes.takeOrNull(intKey))

        assertEquals(1, attributes.computeIfAbsent(intKey) { 1 })
        assertTrue(attributes.contains(intKey))
    }
}
