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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.IllegalStateException
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class TestException(override val message: String?) : IllegalStateException()

@ExperimentalCoroutinesApi
class HttpOperationInterceptorTest {
    private val allHooks = listOf(
        "readBeforeExecution",
        "modifyBeforeSerialization",
        "readBeforeSerialization",
        "readAfterSerialization",
        // "readBeforeRetryLoop",
        // "readBeforeAttempt",
        "modifyBeforeSigning",
        "readBeforeSigning",
        "readAfterSigning",
        "modifyBeforeTransmit",
        "readBeforeTransmit",
        "readAfterTransmit",
        "modifyBeforeDeserialization",
        "readBeforeDeserialization",
        "readAfterDeserialization",
        // "modifyBeforeAttemptCompletion",
        // "readAfterAttempt",
        "modifyBeforeCompletion",
        "readAfterExecution",
    )

    private val hooksInRetryLoop = setOf(
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
    )

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
            trace("readBeforeRetryLoop")
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

    private suspend fun <I, O> roundTripWithInterceptors(
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

        assertFailsWith<TestException> {
            simpleOrderTest(i1, i2, i3)
        }

        val failHookIdx = allHooks.indexOf(failOnHook)
        val beforeFailHooks = allHooks.subList(0, failHookIdx)
            .flatMap {
                listOf("1:$it", "2:$it", "3:$it")
            }

        // TODO - define set of hooks that are inside the retry loop and modify expected based on that

        // we expect:
        // * every hook for each interceptor before the failure
        // * i1 and i2 hooks for the hook that fails
        // * every hook for modifyBeforeCompletion and readAfterExecution
        val expected = beforeFailHooks + listOf(
            "1:$failOnHook",
            "2:$failOnHook",
            // always run
            "1:modifyBeforeCompletion",
            "2:modifyBeforeCompletion",
            "3:modifyBeforeCompletion",
            "1:readAfterExecution",
            "2:readAfterExecution",
            "3:readAfterExecution",
        )

        hooksFired.shouldNotContain("3:$failOnHook")
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

    // FIXME - re-enable when we wire up retries
    @Ignore
    @Test
    fun testModifyBeforeRetryLoopErrors() = runTest {
        simpleFailOrderTest("modifyBeforeRetryLoop")
    }

    @Ignore
    @Test
    fun testReadBeforeAttemptErrors() = runTest {
        simpleFailOrderTest("readBeforeAttempt")
    }

    @Ignore
    @Test
    fun testModifyBeforeSigningErrors() = runTest {
        simpleFailOrderTest("modifyBeforeSigning")
    }

    @Ignore
    @Test
    fun testReadBeforeSigningErrors() = runTest {
        simpleFailOrderTest("readBeforeSigning")
    }

    @Ignore
    @Test
    fun testReadAfterSigningErrors() = runTest {
        simpleFailOrderTest("readAfterSigning")
    }

    @Ignore
    @Test
    fun testModifyBeforeTransmitErrors() = runTest {
        simpleFailOrderTest("modifyBeforeTransmit")
    }

    @Ignore
    @Test
    fun testReadBeforeTransmitErrors() = runTest {
        simpleFailOrderTest("readBeforeTransmit")
    }

    @Ignore
    @Test
    fun testReadAfterTransmitErrors() = runTest {
        simpleFailOrderTest("readAfterTransmit")
    }

    @Ignore
    @Test
    fun testReadBeforeDeserializationErrors() = runTest {
        simpleFailOrderTest("readBeforeDeserialization")
    }

    @Ignore
    @Test
    fun testReadAfterDeserializationErrors() = runTest {
        simpleFailOrderTest("readAfterDeserialization")
    }

    @Ignore
    @Test
    fun testReadAfterAttemptErrors() = runTest {
        simpleFailOrderTest("readAfterAttempt")
    }

    @Ignore
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
}
