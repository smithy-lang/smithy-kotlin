/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import kotlin.test.*

class SdkManagedCloseableTest {
    class MockCloseableImpl : Closeable {
        var isClosed = false
        var closeCount = 0
        override fun close() {
            isClosed = true
            ++closeCount
        }
    }

    @Test
    fun testShareCount() {
        val closeable = MockCloseableImpl()
        val wrapped = SdkManagedCloseable(closeable)

        wrapped.share()
        wrapped.share()
        wrapped.unshare()
        assertFalse(closeable.isClosed)
    }

    @Test
    fun testCloseNoShare() {
        val closeable = MockCloseableImpl()
        val wrapped = SdkManagedCloseable(closeable)

        wrapped.unshare()
        assertTrue(closeable.isClosed)
    }

    @Test
    fun testCloseWithShare() {
        val closeable = MockCloseableImpl()
        val wrapped = SdkManagedCloseable(closeable)

        wrapped.share()
        wrapped.unshare()
        assertTrue(closeable.isClosed)
    }

    @Test
    fun testInnerCloseIdempotent() {
        val closeable = MockCloseableImpl()
        val wrapped = SdkManagedCloseable(closeable)

        wrapped.share()
        wrapped.unshare()
        wrapped.unshare()
        assertTrue(closeable.isClosed)
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun testShareClosedResource() {
        val closeable = MockCloseableImpl()
        val wrapped = SdkManagedCloseable(closeable)

        wrapped.share()
        wrapped.unshare()
        val ex = assertFailsWith<IllegalStateException> {
            wrapped.share()
        }
        assertEquals("caller attempted to share() a fully unshared object", ex.message)
    }
}
