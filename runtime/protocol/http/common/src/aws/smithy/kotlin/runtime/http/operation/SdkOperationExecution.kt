/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.HttpHandler
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.io.Handler
import aws.smithy.kotlin.runtime.io.middleware.MapRequest
import aws.smithy.kotlin.runtime.io.middleware.Middleware
import aws.smithy.kotlin.runtime.io.middleware.Phase
import aws.smithy.kotlin.runtime.util.InternalApi
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue
import aws.smithy.kotlin.runtime.io.middleware.decorate as decorateHandler

typealias SdkHttpRequest = OperationRequest<HttpRequestBuilder>

/**
 * Configure the execution of an operation from [Request] to [Response]
 *
 * An operation has several "phases" of it's lifecycle that can be intercepted and customized.
 */
@InternalApi
class SdkOperationExecution<Request, Response> {

    // technically any phase can act as on the request or the response. The phases
    // are described with their intended use, nothing prevents e.g. registering
    // something in "initialize" that acts on the output type though.

    /**
     * Prepare the input [Request] (or finalize the [Response]) e.g. set any default parameters if needed
     */
    val initialize = Phase<OperationRequest<Request>, Response>()

    /**
     * Modify the outgoing HTTP request
     *
     * At this phase the [Request] (operation input) has been serialized to an HTTP request.
     */
    val mutate = Phase<SdkHttpRequest, Response>()

    /**
     * Last chance to intercept before requests are sent (e.g. signing, retries, etc).
     * First chance to intercept after deserialization.
     */
    val finalize = Phase<SdkHttpRequest, Response>()

    /**
     * First chance to intercept before deserialization into operation output type [Response].
     */
    val receive = Phase<SdkHttpRequest, HttpCall>()
}

/**
 * Decorate the "raw" [HttpHandler] with the execution phases (middleware) of this operation and
 * return a handler to be used specifically for the given operation.
 */
internal fun <Request, Response> SdkOperationExecution<Request, Response>.decorate(
    handler: HttpHandler,
    serializer: HttpSerialize<Request>,
    deserializer: HttpDeserialize<Response>,
): Handler<OperationRequest<Request>, Response> {
    val inner = MapRequest(handler) { sdkRequest: SdkHttpRequest ->
        sdkRequest.subject
    }

    // ensure http calls are tracked
    receive.register(Phase.Order.After, HttpCallMiddleware())
    receive.intercept(Phase.Order.After, ::httpTraceMiddleware)

    val receiveHandler = decorateHandler(inner, receive)
    val deserializeHandler = deserializer.decorate(receiveHandler)
    val finalizeHandler = decorateHandler(FinalizeHandler(deserializeHandler), finalize)
    val mutateHandler = decorateHandler(MutateHandler(finalizeHandler), mutate)
    val serializeHandler = serializer.decorate(mutateHandler)
    return decorateHandler(InitializeHandler(serializeHandler), initialize)
}

private fun <I, O> HttpSerialize<I>.decorate(
    inner: Handler<SdkHttpRequest, O>
): Handler<OperationRequest<I>, O> = SerializeHandler(inner, ::serialize)

private fun <O> HttpDeserialize<O>.decorate(
    inner: Handler<SdkHttpRequest, HttpCall>,
): Handler<SdkHttpRequest, O> = DeserializeHandler(inner, ::deserialize)

// internal glue used to marry one phase to another

private class InitializeHandler<Input, Output>(
    private val inner: Handler<Input, Output>
) : Handler<Input, Output> {
    override suspend fun call(request: Input): Output = inner.call(request)
}

private class SerializeHandler<Input, Output> (
    private val inner: Handler<SdkHttpRequest, Output>,
    private val mapRequest: suspend (ExecutionContext, Input) -> HttpRequestBuilder
) : Handler<OperationRequest<Input>, Output> {

    @OptIn(ExperimentalTime::class)
    override suspend fun call(request: OperationRequest<Input>): Output {
        val tv = measureTimedValue {
            mapRequest(request.context, request.subject)
        }

        request.context.logger.trace { "request serialized in ${tv.duration}" }
        return inner.call(SdkHttpRequest(request.context, tv.value))
    }
}

private class MutateHandler<Output> (
    private val inner: Handler<SdkHttpRequest, Output>
) : Handler<SdkHttpRequest, Output> {
    override suspend fun call(request: SdkHttpRequest): Output = inner.call(request)
}

private class FinalizeHandler<Output> (
    private val inner: Handler<SdkHttpRequest, Output>
) : Handler<SdkHttpRequest, Output> {
    override suspend fun call(request: SdkHttpRequest): Output = inner.call(request)
}

private class DeserializeHandler<Output>(
    private val inner: Handler<SdkHttpRequest, HttpCall>,
    private val mapResponse: suspend (ExecutionContext, HttpResponse) -> Output
) : Handler<SdkHttpRequest, Output> {

    @OptIn(ExperimentalTime::class)
    override suspend fun call(request: SdkHttpRequest): Output {
        val call = inner.call(request)
        val tv = measureTimedValue {
            mapResponse(request.context, call.response)
        }
        request.context.logger.trace { "response deserialized in: ${tv.duration}" }
        return tv.value
    }
}

/**
 * default middleware that handles managing the HTTP call list
 */
class HttpCallMiddleware : Middleware<SdkHttpRequest, HttpCall> {
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
    request.context.logger.trace { "httpRequest: ${request.subject}" }
    val call = next.call(request)
    request.context.logger.trace { "httpResponse: ${call.response}" }
    return call
}
