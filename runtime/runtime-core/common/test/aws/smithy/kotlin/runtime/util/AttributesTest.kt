/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import kotlin.test.*

class AttributesTest {
    @Test
    fun testPropertyBag() {
        val strKey = AttributeKey<String>("string")
        val intKey = AttributeKey<Int>("int")
        val attributes = mutableAttributes()

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
        val attributes = mutableAttributes()
        val strKey = AttributeKey<String>("string")
        attributes[strKey] = "foo"
        attributes.putIfAbsent(strKey, "bar")
        assertEquals("foo", attributes[strKey])
        attributes.remove(strKey)
        attributes.putIfAbsent(strKey, "bar")
        assertEquals("bar", attributes[strKey])
    }

    @Test
    fun testPutIfAbsentNotNull() {
        val attributes = mutableAttributes()
        val strKey = AttributeKey<String>("string")
        attributes[strKey] = "foo"

        attributes.putIfAbsentNotNull(strKey, null)
        assertEquals("foo", attributes[strKey])

        attributes.putIfAbsentNotNull(strKey, "bar")
        assertEquals("foo", attributes[strKey])

        attributes.remove(strKey)

        attributes.putIfAbsentNotNull(strKey, null)
        assertNull(attributes.getOrNull(strKey))

        attributes.putIfAbsentNotNull(strKey, "bar")
        assertEquals("bar", attributes[strKey])
    }

    @Test
    fun testMerge() {
        val attr1 = mutableAttributes()
        val key1 = AttributeKey<String>("k1")
        val key2 = AttributeKey<String>("k2")
        val key3 = AttributeKey<String>("k3")

        attr1[key1] = "Foo"
        attr1[key2] = "Bar"

        val attr2 = mutableAttributes()
        attr2[key2] = "Baz"
        attr2[key3] = "Quux"

        attr1.merge(attr2)

        assertEquals("Foo", attr1[key1])
        assertEquals("Baz", attr1[key2])
        assertEquals("Quux", attr1[key3])
    }

    @Test
    fun testSetIfNotNull() {
        val attributes = mutableAttributes()
        val strKey = AttributeKey<String>("string")
        attributes.setIfValueNotNull(strKey, null)
        assertFalse(attributes.contains(strKey))

        attributes.setIfValueNotNull(strKey, "foo")
        assertEquals("foo", attributes[strKey])
    }

    @Test
    fun testMutableAttributesOf() {
        val attr1 = AttributeKey<String>("string")
        val attr2 = AttributeKey<Int>("int")
        val attributes = mutableAttributesOf {
            attr1 to "foo"
            attr2 to 57
        }

        assertEquals("foo", attributes[attr1])
        assertEquals(57, attributes[attr2])
    }

    @Test
    fun testToMutableAttributes() {
        val attr1 = AttributeKey<String>("string")
        val attr2 = AttributeKey<Int>("int")
        val attrs = attributesOf {
            attr1 to "foo"
            attr2 to 57
        }

        val mutAttrs = attrs.toMutableAttributes()

        assertEquals("foo", mutAttrs[attr1])
        assertEquals(57, mutAttrs[attr2])

        mutAttrs.remove(attr2)
        assertFalse(attr2 in mutAttrs)

        assertEquals("foo", attrs[attr1])
        assertEquals(57, attrs[attr2])
    }
}
