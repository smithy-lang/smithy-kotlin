/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TestLoggingContext {
    @Test
    fun testWithLogCtx() = runTest {
        withLogCtx("foo" to "bar", "tay" to "hammer") {
            val cc = kotlin.coroutines.coroutineContext
            val outerLogCtx = cc.loggingContext
            assertEquals("bar", outerLogCtx["foo"])
            assertEquals("hammer", outerLogCtx["tay"])

            withLogCtx("baz" to "quux", "tay" to "comb") {
                val innerLogCtx = kotlin.coroutines.coroutineContext.loggingContext
                assertEquals("bar", innerLogCtx["foo"])
                assertEquals("quux", innerLogCtx["baz"])
                assertEquals("comb", innerLogCtx["tay"])
            }
        }
    }
}
