/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.client.SdkLogMode
import aws.smithy.kotlin.runtime.client.sdkLogMode
import aws.smithy.kotlin.runtime.http.HttpHandler
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.dumpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.response.dumpResponse
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.io.middleware.Middleware
import aws.smithy.kotlin.runtime.io.middleware.Phase
import aws.smithy.kotlin.runtime.logging.Logger
import aws.smithy.kotlin.runtime.util.InternalApi
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue
import aws.smithy.kotlin.runtime.io.middleware.decorate as decorateHandler

public typealias SdkHttpRequest = OperationRequest<HttpRequestBuilder>

/**
 * Configure the execution of an operation from [Request] to [Response]
 *
 * An operation has several "phases" of its lifecycle that can be intercepted and customized.
 */
@InternalApi
public class SdkOperationExecution<Request, Response> {

    // technically any phase can act as on the request or the response. The phases
    // are described with their intended use, nothing prevents e.g. registering
    // something in "initialize" that acts on the output type though.

    /**
     * Prepare the input [Request] (or finalize the [Response]) e.g. set any default parameters if needed
     */
    public val initialize: Phase<OperationRequest<Request>, Response> = Phase<OperationRequest<Request>, Response>()

    /**
     * Modify the outgoing HTTP request
     *
     * At this phase the [Request] (operation input) has been serialized to an HTTP request.
     */
    public val mutate: Phase<SdkHttpRequest, Response> = Phase<SdkHttpRequest, Response>()

    /**
     * Last chance to intercept before requests are sent (e.g. signing, retries, etc).
     * First chance to intercept after deserialization.
     */
    public val finalize: Phase<SdkHttpRequest, Response> = Phase<SdkHttpRequest, Response>()

    /**
     * First chance to intercept before deserialization into operation output type [Response].
     */
    public val receive: Phase<SdkHttpRequest, HttpCall> = Phase<SdkHttpRequest, HttpCall>()
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
    // ensure http calls are tracked
    receive.register(HttpCallMiddleware())
    receive.intercept(Phase.Order.After, ::httpTraceMiddleware)

    val receiveHandler = decorateHandler(handler, receive)
    val deserializeHandler = deserializer.decorate(receiveHandler)
    val finalizeHandler = decorateHandler(FinalizeHandler(deserializeHandler), finalize)
    val mutateHandler = decorateHandler(MutateHandler(finalizeHandler), mutate)
    val serializeHandler = serializer.decorate(mutateHandler)
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

    companion object {
        // generics aren't propagated on names anyway, just fill in a placeholder for type parameters
        private val logger = Logger.getLogger<SerializeHandler<Unit, Unit>>()
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun call(request: OperationRequest<Input>): Output {
        val tv = measureTimedValue {
            mapRequest(request.context, request.subject)
        }

        logger.withContext(request.context).trace { "request serialized in ${tv.duration}" }
        return inner.call(SdkHttpRequest(request.context, tv.value))
    }
}

private class MutateHandler<Output> (
    private val inner: Handler<SdkHttpRequest, Output>,
) : Handler<SdkHttpRequest, Output> {
    override suspend fun call(request: SdkHttpRequest): Output = inner.call(request)
}

private class FinalizeHandler<Output> (
    private val inner: Handler<SdkHttpRequest, Output>,
) : Handler<SdkHttpRequest, Output> {
    override suspend fun call(request: SdkHttpRequest): Output = inner.call(request)
}

private class DeserializeHandler<Output>(
    private val inner: Handler<SdkHttpRequest, HttpCall>,
    private val mapResponse: suspend (ExecutionContext, HttpResponse) -> Output,
) : Handler<SdkHttpRequest, Output> {

    companion object {
        private val logger = Logger.getLogger<DeserializeHandler<Unit>>()
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun call(request: SdkHttpRequest): Output {
        val call = inner.call(request)
        val tv = measureTimedValue {
            mapResponse(request.context, call.response)
        }
        logger.withContext(request.context).trace { "response deserialized in: ${tv.duration}" }
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
    val logger by lazy { request.context.getLogger("httpTraceMiddleware") }

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
