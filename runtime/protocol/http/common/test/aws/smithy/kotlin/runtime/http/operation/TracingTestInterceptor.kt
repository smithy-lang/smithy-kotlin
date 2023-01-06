/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.sdkHttpClient
import aws.smithy.kotlin.runtime.time.Instant

class TestException(override val message: String?) : IllegalStateException()
data class TestInput(val value: String)
data class TestOutput(val value: String)

class MockHttpClientOptions {
    var failWithRetryableError: Boolean = false
    var failOnAttempts: Set<Int> = emptySet()
    var statusCode: HttpStatusCode = HttpStatusCode.OK
    var responseHeaders: Headers = Headers.Empty
    var responseBody: HttpBody = HttpBody.Empty
}

fun newMockHttpClient(block: MockHttpClientOptions.() -> Unit = {}): SdkHttpClient {
    val options = MockHttpClientOptions().apply(block)
    val mockEngine = object : HttpClientEngineBase("test engine") {
        private var attempt = 0
        override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
            attempt++
            if (attempt in options.failOnAttempts) {
                val ex = if (options.failWithRetryableError) RetryableServiceTestException else TestException("non-retryable exception")
                throw ex
            }

            val resp = HttpResponse(options.statusCode, options.responseHeaders, options.responseBody)
            return HttpCall(request, resp, Instant.now(), Instant.now())
        }
    }
    return sdkHttpClient(mockEngine)
}

suspend fun <I : Any, O : Any> roundTripWithInterceptors(
    input: I,
    op: SdkHttpOperation<I, O>,
    client: SdkHttpClient,
    vararg interceptors: HttpInterceptor,
): O {
    op.interceptors.addAll(interceptors.toList())
    return op.roundTrip(client, input)
}

open class TracingTestInterceptor(
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

    override suspend fun modifyBeforeSerialization(context: RequestInterceptorContext<Any>): Any {
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

    override suspend fun modifyBeforeRetryLoop(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        trace("modifyBeforeRetryLoop")
        return super.modifyBeforeRetryLoop(context)
    }

    override fun readBeforeAttempt(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        trace("readBeforeAttempt")
        super.readBeforeAttempt(context)
    }

    override suspend fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
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

    override suspend fun modifyBeforeTransmit(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
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

    override suspend fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>): HttpResponse {
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

    override suspend fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
        trace("modifyBeforeAttemptCompletion")
        return super.modifyBeforeAttemptCompletion(context)
    }

    override fun readAfterAttempt(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>) {
        trace("readAfterAttempt")
        super.readAfterAttempt(context)
    }

    override suspend fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
        trace("modifyBeforeCompletion")
        return super.modifyBeforeCompletion(context)
    }

    override fun readAfterExecution(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>) {
        trace("readAfterExecution")
        super.readAfterExecution(context)
    }
}
