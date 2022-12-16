/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.time.Instant
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.IllegalStateException
import kotlin.test.*

private data class TestInput(val value: String)
private data class TestOutput(val value: String)

@ExperimentalCoroutinesApi
class HttpOperationInterceptorTest {
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

    private class MockHttpClientOptions {
        var failWithRetryableError: Boolean = false
        var failAfterAttempt: Int = -1
        var statusCode: HttpStatusCode = HttpStatusCode.OK
        var responseHeaders: Headers = Headers.Empty
        var responseBody: HttpBody = HttpBody.Empty
    }

    private fun newMockHttpClient(block: MockHttpClientOptions.() -> Unit = {}): SdkHttpClient {
        val options = MockHttpClientOptions().apply(block)
        val mockEngine = object : HttpClientEngineBase("test engine") {
            private var attempt = 0
            override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
                attempt++
                if (attempt == options.failAfterAttempt) {
                    val ex = if (options.failWithRetryableError) RetryableServiceTestException else TestException("non-retryable exception")
                    throw ex
                }

                val resp = HttpResponse(options.statusCode, options.responseHeaders, options.responseBody)
                return HttpCall(request, resp, Instant.now(), Instant.now())
            }
        }
        return sdkHttpClient(mockEngine)
    }

    private suspend fun <I : Any, O : Any> roundTripWithInterceptors(
        input: I,
        op: SdkHttpOperation<I, O>,
        client: SdkHttpClient,
        vararg interceptors: HttpInterceptor,
    ): O {
        op.interceptors.addAll(interceptors.toList())
        return op.roundTrip(client, input)
    }

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
        val i1 = TestInterceptor("1", hooksFired)
        val i2 = TestInterceptor("2", hooksFired, failOnHooks = setOf(failOnHook))
        val i3 = TestInterceptor("3", hooksFired)
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
        val i1 = TestInterceptor("1", hooksFired)
        val i2 = TestInterceptor("2", hooksFired)

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

    @Test
    fun testModifyBeforeSerializationTypeFailure() = runTest {
        val i1 = object : HttpInterceptor {
            override fun modifyBeforeSerialization(context: RequestInterceptorContext<Any>): Any {
                val input = assertIs<TestInput>(context.request)
                assertEquals("initial", input.value)
                return TestInput("modified")
            }
        }

        val i2 = object : HttpInterceptor {
            override fun modifyBeforeSerialization(context: RequestInterceptorContext<Any>): Any {
                val input = assertIs<TestInput>(context.request)
                assertEquals("modified", input.value)
                return TestOutput("wrong")
            }
        }

        val input = TestInput("initial")
        val op = newTestOperation<TestInput, Unit>(HttpRequestBuilder(), Unit)
        val client = newMockHttpClient()
        val ex = assertFailsWith<IllegalStateException> {
            roundTripWithInterceptors(input, op, client, i1, i2)
        }

        ex.message.shouldContain("modifyBeforeSerialization invalid type conversion: found class aws.smithy.kotlin.runtime.http.operation.TestOutput; expected class aws.smithy.kotlin.runtime.http.operation.TestInput")
    }

    @Test
    fun testModifyBeforeAttemptCompletionTypeFailure() = runTest {
        val i1 = object : HttpInterceptor {
            override fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
                assertIs<TestOutput>(context.response.getOrThrow())
                return Result.success(TestOutput("modified"))
            }
        }

        val i2 = object : HttpInterceptor {
            override fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
                val output = assertIs<TestOutput>(context.response.getOrThrow())
                assertEquals("modified", output.value)
                return Result.success("wrong")
            }
        }

        val output = TestOutput("initial")
        val op = newTestOperation<Unit, TestOutput>(HttpRequestBuilder(), output)
        val client = newMockHttpClient()
        val ex = assertFailsWith<IllegalStateException> {
            roundTripWithInterceptors(Unit, op, client, i1, i2)
        }

        ex.message.shouldContain("modifyBeforeAttemptCompletion invalid type conversion: found class kotlin.String; expected class aws.smithy.kotlin.runtime.http.operation.TestOutput")
    }

    @Test
    fun testModifyBeforeCompletionTypeFailure() = runTest {
        val i1 = object : HttpInterceptor {
            override fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
                assertIs<TestOutput>(context.response.getOrThrow())
                return Result.success(TestOutput("modified"))
            }
        }

        val i2 = object : HttpInterceptor {
            override fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
                val output = assertIs<TestOutput>(context.response.getOrThrow())
                assertEquals("modified", output.value)
                return Result.success("wrong")
            }
        }

        val output = TestOutput("initial")
        val op = newTestOperation<Unit, TestOutput>(HttpRequestBuilder(), output)
        val client = newMockHttpClient()
        val ex = assertFailsWith<IllegalStateException> {
            roundTripWithInterceptors(Unit, op, client, i1, i2)
        }

        ex.message.shouldContain("modifyBeforeCompletion invalid type conversion: found class kotlin.String; expected class aws.smithy.kotlin.runtime.http.operation.TestOutput")
    }
}
