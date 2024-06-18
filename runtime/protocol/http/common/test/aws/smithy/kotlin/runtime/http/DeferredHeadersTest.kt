/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http

import kotlin.test.Test
import kotlin.test.assertEquals

class DeferredHeadersTest {
    @Test
    fun testEmptyEquals() {
        val explicitlyEmpty = DeferredHeaders.Empty
        val implicitlyEmpty = DeferredHeadersBuilder().build()

        assertEquals(implicitlyEmpty, explicitlyEmpty)
        assertEquals(explicitlyEmpty, implicitlyEmpty)

        assertEquals(explicitlyEmpty, explicitlyEmpty)
        assertEquals(implicitlyEmpty, implicitlyEmpty)
    }
}
