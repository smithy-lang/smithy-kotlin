/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.operation

import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.http.HttpHandler
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.io.Handler
import software.aws.clientrt.io.middleware.MapRequest
import software.aws.clientrt.io.middleware.Phase
import software.aws.clientrt.util.InternalAPI
import software.aws.clientrt.io.middleware.decorate as decorateHandler

typealias SdkHttpRequest = OperationRequest<HttpRequestBuilder>

/**
 * Configure the execution of an operation from [Request] to [Response]
 *
 * An operation has several "phases" of it's lifecycle that can be intercepted and customized.
 */
@InternalAPI
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
    val receive = Phase<SdkHttpRequest, HttpResponse>()
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

    val receiveHandler = decorateHandler(inner, receive)
    val deserializeHandler = deserializer.decorate(receiveHandler)
    val finalizeHandler = decorateHandler(FinalizeHandler(deserializeHandler), finalize)
    val mutateHandler = decorateHandler(MutateHandler(finalizeHandler), mutate)
    val serializeHandler = serializer.decorate(mutateHandler)
    return decorateHandler(InitializeHandler(serializeHandler), initialize)
}

private fun <I, O> HttpSerialize<I>.decorate(
    inner: Handler<SdkHttpRequest, O>
): Handler<OperationRequest<I>, O> {
    return SerializeHandler(inner, ::serialize)
}

private fun <O> HttpDeserialize<O>.decorate(
    inner: Handler<SdkHttpRequest, HttpResponse>,
): Handler<SdkHttpRequest, O> {
    return DeserializeHandler(inner, ::deserialize)
}

// internal glue used to marry one phase to another

private class InitializeHandler<Input, Output>(
    private val inner: Handler<Input, Output>
) : Handler<Input, Output> {
    override suspend fun call(request: Input): Output {
        return inner.call(request)
    }
}

private class SerializeHandler<Input, Output> (
    private val inner: Handler<SdkHttpRequest, Output>,
    private val mapRequest: suspend (ExecutionContext, Input) -> HttpRequestBuilder
) : Handler<OperationRequest<Input>, Output> {
    override suspend fun call(request: OperationRequest<Input>): Output {
        val builder = mapRequest(request.context, request.subject)
        return inner.call(SdkHttpRequest(request.context, builder))
    }
}

private class MutateHandler<Output> (
    private val inner: Handler<SdkHttpRequest, Output>
) : Handler<SdkHttpRequest, Output> {

    override suspend fun call(request: SdkHttpRequest): Output {
        return inner.call(request)
    }
}

private class FinalizeHandler<Output> (
    private val inner: Handler<SdkHttpRequest, Output>
) : Handler<SdkHttpRequest, Output> {

    override suspend fun call(request: SdkHttpRequest): Output {
        return inner.call(request)
    }
}

private class DeserializeHandler<Output>(
    private val inner: Handler<SdkHttpRequest, HttpResponse>,
    private val mapResponse: suspend (ExecutionContext, HttpResponse) -> Output
) : Handler<SdkHttpRequest, Output> {

    override suspend fun call(request: SdkHttpRequest): Output {
        // ensure the raw response is stashed in the context
        val rawResponse = inner.call(request)
        request.context[HttpOperationContext.HttpResponse] = rawResponse
        return mapResponse(request.context, rawResponse)
    }
}
