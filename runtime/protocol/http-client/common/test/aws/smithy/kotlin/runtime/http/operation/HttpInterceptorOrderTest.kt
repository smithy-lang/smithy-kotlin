/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@ExperimentalCoroutinesApi
class HttpInterceptorOrderTest {
    private val allHooks = listOf(
        "readBeforeExecution",
        "modifyBeforeSerialization",
        "readBeforeSerialization",
        "readAfterSerialization",
        "modifyBeforeRetryLoop",
        "readBeforeAttempt",
        "modifyBeforeSigning",
        "readBeforeSigning",
        "readAfterSigning",
        "modifyBeforeTransmit",
        "readBeforeTransmit",
        "readAfterTransmit",
        "modifyBeforeDeserialization",
        "readBeforeDeserialization",
        "readAfterDeserialization",
        "modifyBeforeAttemptCompletion",
        "readAfterAttempt",
        "modifyBeforeCompletion",
        "readAfterExecution",
    )

    private val hooksFiredEveryExecution = setOf("readBeforeExecution", "readAfterExecution")
    private val hooksFiredEveryAttempt = setOf("readBeforeAttempt", "readAfterAttempt")

    private suspend fun simpleOrderTest(
        client: SdkHttpClient,
        vararg interceptors: HttpInterceptor,
    ) {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder(), Unit)
        roundTripWithInterceptors(Unit, op, client, *interceptors)
    }
    private suspend fun simpleOrderTest(
        vararg interceptors: HttpInterceptor,
    ) = simpleOrderTest(newMockHttpClient(), *interceptors)

    private suspend fun simpleFailOrderTest(failOnHook: String) {
        val hooksFired = mutableListOf<String>()
        val i1 = TracingTestInterceptor("1", hooksFired)
        val i2 = TracingTestInterceptor("2", hooksFired, failOnHooks = setOf(failOnHook))
        val i3 = TracingTestInterceptor("3", hooksFired)
        val allInterceptors = listOf(i1, i2, i3)

        assertFailsWith<TestException> {
            simpleOrderTest(i1, i2, i3)
        }

        val failHookIdx = allHooks.indexOf(failOnHook)

        val firstAttemptHookIdx = allHooks.indexOf("readBeforeAttempt")
        val madeItToRetryLoop = failHookIdx >= firstAttemptHookIdx
        val modifyBeforeCompletionIdx = allHooks.indexOf("modifyBeforeCompletion")

        val readBeforeExecutionHooks = allInterceptors.map { "${it.id}:readBeforeExecution" }
        val readAfterExecutionHooks = allInterceptors.map { "${it.id}:readAfterExecution" }

        val failHooks = if (failOnHook !in (hooksFiredEveryExecution + hooksFiredEveryAttempt)) {
            listOf("1:$failOnHook", "2:$failOnHook")
        } else {
            emptyList()
        }

        val modifyBeforeCompletionHooks = if (failHookIdx == modifyBeforeCompletionIdx) {
            // accounted for in fail hooks
            emptyList()
        } else {
            allInterceptors.map { "${it.id}:modifyBeforeCompletion" }
        }

        val beforeRetryLoopHooks = if (failHookIdx > 0) {
            allHooks.subList(0, minOf(failHookIdx, firstAttemptHookIdx))
                .flatMap { hook -> allInterceptors.map { "${it.id}:$hook" } }
        } else {
            readBeforeExecutionHooks
        }

        val retryLoopHooks = if (madeItToRetryLoop) {
            val readAttemptHooks = allInterceptors.map { "${it.id}:readBeforeAttempt" }
            val readAfterAttemptHooks = allInterceptors.map { "${it.id}:readAfterAttempt" }
            val modifyBeforeAttemptCompletionIdx = allHooks.indexOf("modifyBeforeAttemptCompletion")

            val perAttemptHooks = if (failHookIdx in (firstAttemptHookIdx + 1)..modifyBeforeAttemptCompletionIdx || failHookIdx > modifyBeforeAttemptCompletionIdx) {
                allHooks.subList(firstAttemptHookIdx + 1, minOf(failHookIdx, modifyBeforeAttemptCompletionIdx))
                    .flatMap { hook -> allInterceptors.map { "${it.id}:$hook" } }
            } else {
                emptyList()
            }

            val modifyBeforeAttemptCompletionHooks = if (failHookIdx == modifyBeforeAttemptCompletionIdx) {
                // accounted for in fail hooks
                emptyList()
            } else {
                allInterceptors.map { "${it.id}:modifyBeforeAttemptCompletion" }
            }

            if (failHookIdx in firstAttemptHookIdx..modifyBeforeAttemptCompletionIdx) {
                readAttemptHooks + perAttemptHooks + failHooks + modifyBeforeAttemptCompletionHooks + readAfterAttemptHooks
            } else {
                readAttemptHooks + perAttemptHooks + modifyBeforeAttemptCompletionHooks + readAfterAttemptHooks
            }
        } else {
            emptyList()
        }

        val middle = when {
            // fail hooks came before retry loop
            failHookIdx < firstAttemptHookIdx -> failHooks
            // fail hook accounted for by retry loop
            failHookIdx in firstAttemptHookIdx until modifyBeforeCompletionIdx -> retryLoopHooks
            // fail hook after retry loop
            else -> retryLoopHooks + failHooks
        }

        val expected = beforeRetryLoopHooks + middle + modifyBeforeCompletionHooks + readAfterExecutionHooks

        if (failOnHook !in (hooksFiredEveryExecution + hooksFiredEveryAttempt)) {
            hooksFired.shouldNotContain("3:$failOnHook")
        }
        hooksFired.shouldContainInOrder(expected)
    }

    @Test
    fun testInterceptorOrderSuccess() = runTest {
        // sanity test all hooks fire in order
        val hooksFired = mutableListOf<String>()
        val i1 = TracingTestInterceptor("1", hooksFired)
        val i2 = TracingTestInterceptor("2", hooksFired)

        simpleOrderTest(i1, i2)

        val expected = allHooks.flatMap {
            listOf("1:$it", "2:$it")
        }

        assertEquals(expected, hooksFired)
    }

    @Test
    fun testReadBeforeExecutionErrors() = runTest {
        simpleFailOrderTest("readBeforeExecution")
    }

    @Test
    fun testModifyBeforeSerializationErrors() = runTest {
        simpleFailOrderTest("modifyBeforeSerialization")
    }

    @Test
    fun testReadBeforeSerializationErrors() = runTest {
        simpleFailOrderTest("readBeforeSerialization")
    }

    @Test
    fun testReadAfterSerializationErrors() = runTest {
        simpleFailOrderTest("readAfterSerialization")
    }

    @Test
    fun testModifyBeforeRetryLoopErrors() = runTest {
        simpleFailOrderTest("modifyBeforeRetryLoop")
    }

    @Test
    fun testReadBeforeAttemptErrors() = runTest {
        simpleFailOrderTest("readBeforeAttempt")
    }

    @Test
    fun testModifyBeforeSigningErrors() = runTest {
        simpleFailOrderTest("modifyBeforeSigning")
    }

    @Test
    fun testReadBeforeSigningErrors() = runTest {
        simpleFailOrderTest("readBeforeSigning")
    }

    @Test
    fun testReadAfterSigningErrors() = runTest {
        simpleFailOrderTest("readAfterSigning")
    }

    @Test
    fun testModifyBeforeTransmitErrors() = runTest {
        simpleFailOrderTest("modifyBeforeTransmit")
    }

    @Test
    fun testReadBeforeTransmitErrors() = runTest {
        simpleFailOrderTest("readBeforeTransmit")
    }

    @Test
    fun testReadAfterTransmitErrors() = runTest {
        simpleFailOrderTest("readAfterTransmit")
    }

    @Test
    fun testReadBeforeDeserializationErrors() = runTest {
        simpleFailOrderTest("readBeforeDeserialization")
    }

    @Test
    fun testReadAfterDeserializationErrors() = runTest {
        simpleFailOrderTest("readAfterDeserialization")
    }

    @Test
    fun testReadAfterAttemptErrors() = runTest {
        simpleFailOrderTest("readAfterAttempt")
    }

    @Test
    fun testModifyBeforeAttemptCompletionErrors() = runTest {
        simpleFailOrderTest("modifyBeforeAttemptCompletion")
    }

    @Test
    fun testModifyBeforeCompletionErrors() = runTest {
        simpleFailOrderTest("modifyBeforeCompletion")
    }

    @Test
    fun testReadAfterExecutionErrors() = runTest {
        simpleFailOrderTest("readAfterExecution")
    }
}
