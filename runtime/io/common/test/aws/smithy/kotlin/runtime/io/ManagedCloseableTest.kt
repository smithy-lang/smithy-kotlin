/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import kotlin.test.*

class ManagedCloseableTest {
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
        val wrapped = ManagedCloseable(closeable)

        wrapped.share()
        wrapped.share()
        wrapped.close()
        assertFalse(closeable.isClosed)
    }

    @Test
    fun testCloseNoShare() {
        val closeable = MockCloseableImpl()
        val wrapped = ManagedCloseable(closeable)

        wrapped.close()
        assertTrue(closeable.isClosed)
    }

    @Test
    fun testCloseWithShare() {
        val closeable = MockCloseableImpl()
        val wrapped = ManagedCloseable(closeable)

        wrapped.share()
        wrapped.close()
        assertTrue(closeable.isClosed)
    }

    @Test
    fun testInnerCloseIdempotent() {
        val closeable = MockCloseableImpl()
        val wrapped = ManagedCloseable(closeable)

        wrapped.share()
        wrapped.close()
        wrapped.close()
        assertTrue(closeable.isClosed)
        assertEquals(1, closeable.closeCount)
    }
}
