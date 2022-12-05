/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.tracing

import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TraceSpanTest {
    @Test
    fun testRootSpanNoExisting() = runTest {
        val tracer = DefaultTracer(NoOpTraceProbe, "test")
        val rootSpan = tracer.createRootSpan("root")
        coroutineContext.withRootTraceSpan(rootSpan) {
            val actualSpan = coroutineContext.traceSpan
            assertEquals(rootSpan, actualSpan)
        }
    }

    @Test
    fun testRootSpanExistingIsParent() = runTest {
        val tracer = DefaultTracer(NoOpTraceProbe, "test")
        val rootSpan = tracer.createRootSpan("root")

        coroutineContext.withRootTraceSpan(rootSpan) {
            val currSpan = coroutineContext.traceSpan
            assertNull(currSpan.parent)
            val nestedTracer = currSpan.asNestedTracer("nested")
            val nestedRoot = nestedTracer.createRootSpan("root2")
            // this should be allowed since the existing span is the parent
            coroutineContext.withRootTraceSpan(nestedRoot) {
                val actualSpan = coroutineContext.traceSpan
                assertEquals(currSpan, actualSpan.parent)
            }
        }
    }

    @Test
    fun testRootSpanExistingIsNotParent() = runTest {
        val tracer = DefaultTracer(NoOpTraceProbe, "test")
        val rootSpan1 = tracer.createRootSpan("root1")
        val rootSpan2 = tracer.createRootSpan("root2")
        val illegalRoot = object : TraceSpan {
            override val parent: TraceSpan = rootSpan2
            override val id: String = "illegal span"
            override fun postEvent(event: TraceEvent) {}
            override fun child(id: String): TraceSpan { error("not needed for test") }
            override fun close() {}
        }

        coroutineContext.withRootTraceSpan(rootSpan1) {
            val outerSpan = coroutineContext.traceSpan
            assertFailsWith<IllegalStateException> {
                coroutineContext.withRootTraceSpan(illegalRoot) { Unit }
            }.message.shouldContain("when no current span exists or the new span is a child of the active span")
        }
    }

    @Test
    fun testRootSpanExistingNoParent() = runTest {
        // existing span with a new root created with no lineage, should become a child of the outer span automatically
        val tracer = DefaultTracer(NoOpTraceProbe, "test")
        val rootSpan1 = tracer.createRootSpan("root1")
        val rootSpan2 = tracer.createRootSpan("root2")

        coroutineContext.withRootTraceSpan(rootSpan1) {
            val outerSpan = coroutineContext.traceSpan
            coroutineContext.withRootTraceSpan(rootSpan2) {
                val innerSpan = coroutineContext.traceSpan
                assertEquals(outerSpan, innerSpan.parent)
            }
        }
    }
}
