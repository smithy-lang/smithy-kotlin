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
            assertNull(currSpan.context.parentId)

            // this should be allowed since the existing span is the parent
            withSpan("root2") {
                val actualSpan = coroutineContext.traceSpan
                assertEquals(currSpan.context.spanId, actualSpan.context.parentId)
            }
        }
    }

    @Test
    fun testRootSpanExistingIsNotParent() = runTest {
        val tracer = DefaultTracer(NoOpTraceProbe)
        val rootSpan1 = tracer.createSpan("root1")
        val rootSpan2 = tracer.createSpan("root2")
        val illegalRoot = object : TraceSpan {
            override val name: String = "illegal root"
            override val context: TraceContext = object : TraceContext {
                override val parentId: String? = rootSpan2.context.parentId
                override val traceId: String = rootSpan2.context.traceId
                override val spanId: String = "illegal span id"
            }
            override var spanStatus: TraceSpanStatus = TraceSpanStatus.UNSET
            override fun <T : Any> setAttr(key: String, value: T) {}
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
