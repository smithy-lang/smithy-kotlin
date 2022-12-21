/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import kotlin.test.*

class SharedCloseableTest {
    class MockCloseableImpl : Closeable {
        var isClosed = false
        override fun close() {
            isClosed = true
        }
    }

    @Test
    fun testShareCount() {
        val closeable = MockCloseableImpl()
        val wrapped = SharedCloseableImpl(closeable)

        wrapped.share()
        wrapped.share()
        wrapped.close()
        assertFalse(closeable.isClosed)
    }

    @Test
    fun testCloseNoShare() {
        val closeable = MockCloseableImpl()
        val wrapped = SharedCloseableImpl(closeable)

        wrapped.close()
        assertTrue(closeable.isClosed)
    }

    @Test
    fun testCloseWithShare() {
        val closeable = MockCloseableImpl()
        val wrapped = SharedCloseableImpl(closeable)

        wrapped.share()
        wrapped.close()
        assertTrue(closeable.isClosed)
    }
}
