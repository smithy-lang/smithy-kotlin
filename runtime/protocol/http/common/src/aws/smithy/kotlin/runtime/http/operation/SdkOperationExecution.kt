/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.client.SdkLogMode
import aws.smithy.kotlin.runtime.client.sdkLogMode
import aws.smithy.kotlin.runtime.http.HttpHandler
import aws.smithy.kotlin.runtime.http.auth.HttpSigner
import aws.smithy.kotlin.runtime.http.middleware.RetryMiddleware
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.dumpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.response.dumpResponse
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.io.middleware.Middleware
import aws.smithy.kotlin.runtime.io.middleware.Phase
import aws.smithy.kotlin.runtime.retries.RetryStrategy
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import aws.smithy.kotlin.runtime.retries.policy.StandardRetryPolicy
import aws.smithy.kotlin.runtime.tracing.trace
import aws.smithy.kotlin.runtime.util.InternalApi
import kotlin.coroutines.coroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue
import aws.smithy.kotlin.runtime.io.middleware.decorate as decorateHandler

public typealias SdkHttpRequest = OperationRequest<HttpRequestBuilder>

/**
 * Configure the execution of an operation from [Request] to [Response].
 *
 * An operation has several "phases" of its lifecycle that can be intercepted and customized.
 * Technically any phase can act on the request or the response. The phases are named with their intended use; however,
 * nothing prevents e.g. registering something in [initialize] that acts on the output type though.
 *
 * ## Middleware Phases
 *
 * ```
 * Initialize ---> <Serialize> ---> Mutate
 *                                     |
 *                                     v
 *                                  <Retry>
 *                                     |
 *                                     v
 *                              +----------------+
 *                              |  OnEachAttempt |
 *                              |      |         |
 *                              |      v         |
 *                              | <Deserialize>  |
 *                              |      |         |
 *                              |      v         |
 *                              |   <Signing>    |
 *                              |      |         |
 *                              |      v         |
 *                              |   Receive      |
 *                              |      |         |
 *                              |      v         |
 *                              |  <HttpClient>  |
 *                              +----------------+
 * ```
 *
 * In the above diagram the phase relationships and sequencing are shown. Requests start at [initialize] and proceed
 * until the actual HTTP client engine call. The response then is returned up the call stack and traverses the phases
 * in reverse.
 *
 * Phases enclosed in brackets `<>` are implicit phases that are always present and cannot be intercepted directly
 * (only one is allowed to exist). These are usually configured directly when the operation is built.
 *
 * ### Default Behaviors
 *
 * By default, every operation is:
 * * Retried using the configured [retryStrategy] and [retryPolicy].
 * * Signed using the configured [signer]
 */
@InternalApi
public class SdkOperationExecution<Request, Response> {

    /**
     * **Request Middlewares**: Prepare the input [Request] (e.g. set any default parameters if needed) before
     * serialization
     *
     * **Response Middlewares**: Finalize the [Response] before returning it to the caller
     */
    public val initialize: Phase<OperationRequest<Request>, Response> = Phase<OperationRequest<Request>, Response>()

    /**
     * **Request Middlewares**: Modify the outgoing HTTP request. This phase runs BEFORE the retry loop and
     * is suitable for any middleware that only needs to run once and does not need to modify the outgoing request
     * per/attempt.
     *
     * At this phase the [Request] (operation input) has been serialized to an HTTP request.
     *
     * **Response Middlewares**: Modify the output after deserialization
     */
    public val mutate: Phase<SdkHttpRequest, Response> = Phase<SdkHttpRequest, Response>()

    /**
     * **Request Middlewares**: Modify the outgoing HTTP request. This phase is conceptually the same as [mutate]
     * but it runs on every attempt. Each attempt will not see modifications made by previous attempts.
     *
     * **Response Middlewares**: Modify the output after deserialization on a per/attempt basis.
     */
    public val onEachAttempt: Phase<SdkHttpRequest, Response> = Phase<SdkHttpRequest, Response>()

    /**
     * **Request Middlewares**: First chance to intercept after signing (and last chance before the final request is sent).
     *
     * **Response Middlewares**: First chance to intercept after the raw HTTP response is received and before deserialization
     * into operation output type [Response].
     */
    public val receive: Phase<SdkHttpRequest, HttpCall> = Phase<SdkHttpRequest, HttpCall>()

    /**
     * The [HttpSigner] to sign the request with
     */
    // FIXME - this is temporary until we refactor identity/auth APIs
    public var signer: HttpSigner = HttpSigner.Anonymous

    /**
     * The retry strategy to use. Defaults to [StandardRetryStrategy]
     */
    public var retryStrategy: RetryStrategy = StandardRetryStrategy()

    /**
     * The retry policy to use. Defaults to [StandardRetryPolicy.Default]
     */
    public var retryPolicy: RetryPolicy<Response> = StandardRetryPolicy.Default
}

/**
 * Decorate the "raw" [HttpHandler] with the execution phases (middleware) of this operation and
 * return a handler to be used specifically for the given operation.
 */
internal fun <Request, Response> SdkOperationExecution<Request, Response>.decorate(
    handler: HttpHandler,
    op: SdkHttpOperation<Request, Response>,
): Handler<OperationRequest<Request>, Response> {
    // ensure http calls are tracked
    receive.register(HttpCallMiddleware())
    receive.intercept(Phase.Order.After, ::httpTraceMiddleware)

    val receiveHandler = decorateHandler(handler, receive)
    val authHandler = HttpAuthHandler(receiveHandler, signer)
    val deserializeHandler = op.deserializer.decorate(authHandler)
    val onEachAttemptHandler = decorateHandler(deserializeHandler, onEachAttempt)
    val retryHandler = decorateHandler(onEachAttemptHandler, RetryMiddleware(retryStrategy, retryPolicy))

    val mutateHandler = decorateHandler(MutateHandler(retryHandler), mutate)
    val serializeHandler = op.serializer.decorate(mutateHandler)
    return decorateHandler(InitializeHandler(serializeHandler), initialize)
}

private fun <I, O> HttpSerialize<I>.decorate(
    inner: Handler<SdkHttpRequest, O>,
): Handler<OperationRequest<I>, O> = SerializeHandler(inner, ::serialize)

private fun <O> HttpDeserialize<O>.decorate(
    inner: Handler<SdkHttpRequest, HttpCall>,
): Handler<SdkHttpRequest, O> = DeserializeHandler(inner, ::deserialize)

// internal glue used to marry one phase to another

private class InitializeHandler<Input, Output>(
    private val inner: Handler<Input, Output>,
) : Handler<Input, Output> {
    override suspend fun call(request: Input): Output = inner.call(request)
}

private class SerializeHandler<Input, Output> (
    private val inner: Handler<SdkHttpRequest, Output>,
    private val mapRequest: suspend (ExecutionContext, Input) -> HttpRequestBuilder,
) : Handler<OperationRequest<Input>, Output> {

    @OptIn(ExperimentalTime::class)
    override suspend fun call(request: OperationRequest<Input>): Output {
        val tv = measureTimedValue {
            mapRequest(request.context, request.subject)
        }

        coroutineContext.trace<SerializeHandler<*, *>> { "request serialized in ${tv.duration}" }
        return inner.call(SdkHttpRequest(request.context, tv.value))
    }
}

private class MutateHandler<Output> (
    private val inner: Handler<SdkHttpRequest, Output>,
) : Handler<SdkHttpRequest, Output> {
    override suspend fun call(request: SdkHttpRequest): Output = inner.call(request)
}

private class HttpAuthHandler<Output>(
    private val inner: Handler<SdkHttpRequest, Output>,
    private val signer: HttpSigner,
) : Handler<SdkHttpRequest, Output> {
    override suspend fun call(request: SdkHttpRequest): Output {
        signer.sign(request.context, request.subject)
        return inner.call(request)
    }
}

private class DeserializeHandler<Output>(
    private val inner: Handler<SdkHttpRequest, HttpCall>,
    private val mapResponse: suspend (ExecutionContext, HttpResponse) -> Output,
) : Handler<SdkHttpRequest, Output> {

    @OptIn(ExperimentalTime::class)
    override suspend fun call(request: SdkHttpRequest): Output {
        val call = inner.call(request)
        val tv = measureTimedValue {
            mapResponse(request.context, call.response)
        }
        coroutineContext.trace<DeserializeHandler<*>> { "response deserialized in: ${tv.duration}" }
        return tv.value
    }
}

/**
 * default middleware that handles managing the HTTP call list
 */
private class HttpCallMiddleware : Middleware<SdkHttpRequest, HttpCall> {
    private val callList: MutableList<HttpCall> = mutableListOf()

    override suspend fun <H : Handler<SdkHttpRequest, HttpCall>> handle(request: SdkHttpRequest, next: H): HttpCall {
        if (callList.isNotEmpty()) {
            // an existing call was made and we are retrying for some reason, ensure the resources from the previous
            // attempt are released
            callList.last().complete()
        }
        val call = next.call(request)
        callList.add(call)

        request.context[HttpOperationContext.HttpCallList] = callList
        return call
    }
}

/**
 * default middleware that logs requests/responses
 */
private suspend fun httpTraceMiddleware(request: SdkHttpRequest, next: Handler<SdkHttpRequest, HttpCall>): HttpCall {
    val logMode = request.context.sdkLogMode
    val logger = coroutineContext.getLogger("httpTraceMiddleware")

    if (logMode.isEnabled(SdkLogMode.LogRequest) || logMode.isEnabled(SdkLogMode.LogRequestWithBody)) {
        val formattedReq = dumpRequest(request.subject, logMode.isEnabled(SdkLogMode.LogRequestWithBody))
        logger.debug { "HttpRequest:\n$formattedReq" }
    }

    var call = next.call(request)

    if (logMode.isEnabled(SdkLogMode.LogResponse) || logMode.isEnabled(SdkLogMode.LogResponseWithBody)) {
        val (resp, formattedResp) = dumpResponse(call.response, logMode.isEnabled(SdkLogMode.LogResponseWithBody))
        call = call.copy(response = resp)
        logger.debug { "HttpResponse:\n$formattedResp" }
    } else {
        logger.debug { "HttpResponse: ${call.response.status}" }
    }

    return call
}
