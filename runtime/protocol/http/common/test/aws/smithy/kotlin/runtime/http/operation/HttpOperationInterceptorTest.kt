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

private class TestException(override val message: String?) : IllegalStateException()
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

    // private val hooksInRetryLoop = setOf(
    //     "readBeforeAttempt",
    //     "modifyBeforeSigning",
    //     "readBeforeSigning",
    //     "readAfterSigning",
    //     "modifyBeforeTransmit",
    //     "readBeforeTransmit",
    //     "readAfterTransmit",
    //     "modifyBeforeDeserialization",
    //     "readBeforeDeserialization",
    //     "readAfterDeserialization",
    //     "modifyBeforeAttemptCompletion",
    //     "readAfterAttempt",
    // )
    //
    private val hooksFiredEveryExecution = setOf("readBeforeExecution", "readAfterExecution")
    private val hooksFiredEveryAttempt = setOf("readBeforeAttempt", "readAfterAttempt")

    open class TestInterceptor(
        val id: String,
        val hooksFired: MutableList<String> = mutableListOf<String>(),
        val failOnHooks: Set<String> = emptySet(),
    ) : HttpInterceptor {

        private fun trace(hook: String) {
            hooksFired.add("$id:$hook")
            if (hook in failOnHooks) {
                throw TestException("interceptor $id failed on $hook")
            }
        }

        override fun readBeforeExecution(context: RequestInterceptorContext<Any>) {
            trace("readBeforeExecution")
        }

        override fun modifyBeforeSerialization(context: RequestInterceptorContext<Any>): Any {
            trace("modifyBeforeSerialization")
            return super.modifyBeforeSerialization(context)
        }

        override fun readBeforeSerialization(context: RequestInterceptorContext<Any>) {
            trace("readBeforeSerialization")
            super.readBeforeSerialization(context)
        }

        override fun readAfterSerialization(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            trace("readAfterSerialization")
            super.readAfterSerialization(context)
        }

        override fun modifyBeforeRetryLoop(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
            trace("modifyBeforeRetryLoop")
            return super.modifyBeforeRetryLoop(context)
        }

        override fun readBeforeAttempt(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            trace("readBeforeAttempt")
            super.readBeforeAttempt(context)
        }

        override fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
            trace("modifyBeforeSigning")
            return super.modifyBeforeSigning(context)
        }

        override fun readBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            trace("readBeforeSigning")
            super.readBeforeSigning(context)
        }

        override fun readAfterSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            trace("readAfterSigning")
            super.readAfterSigning(context)
        }

        override fun modifyBeforeTransmit(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
            trace("modifyBeforeTransmit")
            return super.modifyBeforeTransmit(context)
        }

        override fun readBeforeTransmit(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
            trace("readBeforeTransmit")
            super.readBeforeTransmit(context)
        }

        override fun readAfterTransmit(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
            trace("readAfterTransmit")
            super.readAfterTransmit(context)
        }

        override fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>): HttpResponse {
            trace("modifyBeforeDeserialization")
            return super.modifyBeforeDeserialization(context)
        }

        override fun readBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
            trace("readBeforeDeserialization")
            super.readBeforeDeserialization(context)
        }

        override fun readAfterDeserialization(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse>) {
            trace("readAfterDeserialization")
            super.readAfterDeserialization(context)
        }

        override fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
            trace("modifyBeforeAttemptCompletion")
            return super.modifyBeforeAttemptCompletion(context)
        }

        override fun readAfterAttempt(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>) {
            trace("readAfterAttempt")
            super.readAfterAttempt(context)
        }

        override fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
            trace("modifyBeforeCompletion")
            return super.modifyBeforeCompletion(context)
        }

        override fun readAfterExecution(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>) {
            trace("readAfterExecution")
            super.readAfterExecution(context)
        }
    }

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
        val afterExecutionHooks = allHooks.subList(modifyBeforeCompletionIdx, allHooks.size)
            .flatMap { hook -> allInterceptors.map { "${it.id}:$hook" } }

        val expected = if (!madeItToRetryLoop) {
            // if we fail before first attempt then none of the retry hooks will fire
            // we expect:
            // * every hook for each interceptor before the failure
            // * i1 and i2 hooks for the hook that fails
            // * every hook for modifyBeforeCompletion and readAfterExecution
            val beforeFailHooks = allHooks.subList(0, failHookIdx)
                .flatMap { hook -> allInterceptors.map { "${it.id}:$hook" } }

            beforeFailHooks + listOf("1:$failOnHook", "2:$failOnHook") + afterExecutionHooks
        } else {
            // if we reach the retry loop we expect
            // * every hook up to the readBeforeAttempt
            // * readBeforeAttempt for every interceptor
            // * sublist between readBeforeAttempt and the failure hook
            // * failure hook for i1 and i2
            // * modifyBeforeAttemptCompletion + readAfterAttempt for every interceptor

            val beforeAttemptHooks = allHooks.subList(0, firstAttemptHookIdx)
                .flatMap { hook -> allInterceptors.map { "${it.id}:$hook" } }

            val readAttemptHooks = allInterceptors.map { "${it.id}:readBeforeAttempt" }

            val modifyBeforeAttemptCompletionIdx = allHooks.indexOf("modifyBeforeAttemptCompletion")
            val perAttemptHooks = if (failHookIdx in (firstAttemptHookIdx + 1)..modifyBeforeAttemptCompletionIdx) {
                allHooks.subList(firstAttemptHookIdx + 1, failHookIdx)
                    .flatMap { hook -> allInterceptors.map { "${it.id}:$hook" } }
            } else {
                emptyList()
            }

            val failHooks = if (failOnHook !in hooksFiredEveryAttempt) {
                listOf("1:$failOnHook", "2:$failOnHook")
            } else {
                emptyList()
            }

            val modifyBeforeAttemptCompletionHooks = if (failHookIdx == modifyBeforeAttemptCompletionIdx) {
                // accounted for in fail hooks
                emptyList()
            } else {
                allInterceptors.map { "${it.id}:modifyBeforeAttemptCompletion" }
            }

            // val modifyBeforeAttemptCompletionHooks = if (failHookIdx == modifyBeforeAttemptCompletionIdx) {
            //     listOf(i1, i2).map{"${it.id}:modifyBeforeAttemptCompletion"}
            // }else {
            //     allInterceptors.map{"${it.id}:modifyBeforeAttemptCompletion"}
            // }
            val readAfterAttemptHooks = allInterceptors.map { "${it.id}:readAfterAttempt" }
            val afterAttemptHooks = modifyBeforeAttemptCompletionHooks + readAfterAttemptHooks

            beforeAttemptHooks + readAttemptHooks + perAttemptHooks + failHooks + afterAttemptHooks + afterExecutionHooks
        }

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
        val hooksFired = mutableListOf<String>()
        val i1 = TestInterceptor("1", hooksFired, failOnHooks = setOf("readBeforeExecution"))
        val i2 = TestInterceptor("2", hooksFired)
        val i3 = TestInterceptor("3", hooksFired)

        assertFailsWith<TestException> {
            simpleOrderTest(i1, i2, i3)
        }

        val expected = listOf(
            "1:readBeforeExecution",
            "2:readBeforeExecution",
            "3:readBeforeExecution",
            "1:modifyBeforeCompletion",
            "2:modifyBeforeCompletion",
            "3:modifyBeforeCompletion",
            "1:readAfterExecution",
            "2:readAfterExecution",
            "3:readAfterExecution",
        )

        hooksFired.shouldContainInOrder(expected)
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
        val hooksFired = mutableListOf<String>()
        val i1 = TestInterceptor("1", hooksFired)
        val i2 = TestInterceptor("2", hooksFired, failOnHooks = setOf("modifyBeforeCompletion"))
        val i3 = TestInterceptor("3", hooksFired)

        assertFailsWith<TestException> {
            simpleOrderTest(i1, i2, i3)
        }

        val expected = listOf(
            "1:modifyBeforeCompletion",
            "2:modifyBeforeCompletion",
            "1:readAfterExecution",
            "2:readAfterExecution",
            "3:readAfterExecution",
        )

        hooksFired.shouldNotContain("3:modifyBeforeCompletion")
        hooksFired.shouldContainInOrder(expected)
    }

    @Test
    fun testReadAfterExecutionErrors() = runTest {
        val hooksFired = mutableListOf<String>()
        val i1 = TestInterceptor("1", hooksFired, failOnHooks = setOf("readAfterExecution"))
        val i2 = TestInterceptor("2", hooksFired)
        val i3 = TestInterceptor("3", hooksFired)

        assertFailsWith<TestException> {
            simpleOrderTest(i1, i2, i3)
        }

        val expected = listOf(
            "1:readAfterExecution",
            "2:readAfterExecution",
            "3:readAfterExecution",
        )

        hooksFired.shouldContainInOrder(expected)
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
