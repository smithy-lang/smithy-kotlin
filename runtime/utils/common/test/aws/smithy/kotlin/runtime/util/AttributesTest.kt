/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

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

    @Test
    fun testPutIfAbsent() {
        val attributes = Attributes()
        val strKey = AttributeKey<String>("string")
        attributes[strKey] = "foo"
        attributes.putIfAbsent(strKey, "bar")
        assertEquals("foo", attributes[strKey])
        attributes.remove(strKey)
        attributes.putIfAbsent(strKey, "bar")
        assertEquals("bar", attributes[strKey])
    }

    @Test
    fun testMerge() {
        val attr1 = Attributes()
        val key1 = AttributeKey<String>("k1")
        val key2 = AttributeKey<String>("k2")
        val key3 = AttributeKey<String>("k3")

        attr1[key1] = "Foo"
        attr1[key2] = "Bar"

        val attr2 = Attributes()
        attr2[key2] = "Baz"
        attr2[key3] = "Quux"

        attr1.merge(attr2)

        assertEquals("Foo", attr1[key1])
        assertEquals("Baz", attr1[key2])
        assertEquals("Quux", attr1[key3])
    }

    @Test
    fun testSetIfNotNull() {
        val attributes = Attributes()
        val strKey = AttributeKey<String>("string")
        attributes.setIfNotNull(strKey, null)
        assertFalse(attributes.contains(strKey))

        attributes.setIfNotNull(strKey, "foo")
        assertEquals("foo", attributes[strKey])
    }
}
