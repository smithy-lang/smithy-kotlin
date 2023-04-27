/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.tracing

import aws.smithy.kotlin.runtime.util.MutableAttributes
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TraceSpanCoroutineUtilsTest {
    @Test
    fun testRootSpanNoExisting() = runTest {
        val tracer = DefaultTracer(NoOpTraceProbe)
        val rootSpan = tracer.createSpan("root")
        withSpan(rootSpan) {
            val actualSpan = coroutineContext.traceSpan
            assertEquals(rootSpan, actualSpan)
        }
    }

    @Test
    fun testRootSpanExistingIsParent() = runTest {
        val tracer = DefaultTracer(NoOpTraceProbe)
        val rootSpan = tracer.createSpan("root")

        withSpan(rootSpan) { currSpan ->
            assertNull(currSpan.parent)

            // this should be allowed since the existing span is the parent
            withSpan("root2") {
                val actualSpan = coroutineContext.traceSpan
                assertEquals(currSpan, actualSpan.parent)
            }
        }
    }

    @Test
    fun testRootSpanExistingIsNotParent() = runTest {
        val tracer = DefaultTracer(NoOpTraceProbe)
        val rootSpan1 = tracer.createSpan("root1")
        val rootSpan2 = tracer.createSpan("root2")
        val illegalRoot = object : TraceSpan {
            override val parent: TraceSpan = rootSpan2
            override val id: String = "illegal span"
            override val attributes: MutableAttributes
                get() = error("not needed for test")
            override val metadata: TraceSpanMetadata
                get() = error("not needed for test")

            override fun postEvent(event: TraceEvent) {}
            override fun child(name: String): TraceSpan { error("not needed for test") }
            override fun close() {}
        }

        withSpan(rootSpan1) {
            assertFailsWith<IllegalStateException> {
                withSpan(illegalRoot) { Unit }
            }.message.shouldContain("when no current span exists or the new span is a child of the active span")
        }
    }
}
