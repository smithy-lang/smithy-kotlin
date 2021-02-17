/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.operation

import software.aws.clientrt.http.HttpService
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.io.Service
import software.aws.clientrt.io.middleware.MapRequest
import software.aws.clientrt.io.middleware.Phase
import software.aws.clientrt.util.InternalAPI

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
     * Prepare the input [Request] and set any default parameters if needed
     */
    val initialize = Phase<OperationRequest<Request>, Response>()

    /**
     * Modify the outgoing HTTP request
     *
     * At this phase the [Request] (operation input) has been serialized to an HTTP request.
     */
    val state = Phase<SdkHttpRequest, Response>()

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
 * Decorate the "raw" [HttpService] with the execution phases (middleware) of this operation and
 * return a service to be used specifically for the given operation.
 */
internal fun <Request, Response> SdkOperationExecution<Request, Response>.decorate(
    service: HttpService,
    serializer: HttpSerialize<Request>,
    deserializer: HttpDeserialize<Response>,
): Service<OperationRequest<Request>, Response> {

    val inner = MapRequest(service) { sdkRequest: SdkHttpRequest ->
        sdkRequest.request
    }

    val receiveService = software.aws.clientrt.io.middleware.decorate(inner, receive)
    val deserializeService = deserializer.decorate(receiveService)
    val finalizeService = software.aws.clientrt.io.middleware.decorate(FinalizeService(deserializeService), finalize)
    val stateService = software.aws.clientrt.io.middleware.decorate(StateService(finalizeService), state)
    val serializeService = serializer.decorate(stateService)
    return software.aws.clientrt.io.middleware.decorate(InitializeService(serializeService), initialize)
}

private fun <I, O> HttpSerialize<I>.decorate(
    inner: Service<SdkHttpRequest, O>
): Service<OperationRequest<I>, O> {
    val mapRequest: suspend (input: I) -> HttpRequestBuilder = { input ->
        HttpRequestBuilder().also {
            serialize(it, input)
        }
    }
    return SerializeService(inner, mapRequest)
}

private fun <O> HttpDeserialize<O>.decorate(
    inner: Service<SdkHttpRequest, HttpResponse>,
): Service<SdkHttpRequest, O> {
    return DeserializeService(inner, ::deserialize)
}

// internal glue used to marry one phase to another

private class InitializeService<Input, Output>(
    private val inner: Service<Input, Output>
) : Service<Input, Output> {
    override suspend fun call(request: Input): Output {
        return inner.call(request)
    }
}

private class SerializeService<Input, Output> (
    private val inner: Service<SdkHttpRequest, Output>,
    private val mapRequest: suspend (Input) -> HttpRequestBuilder
) : Service<OperationRequest<Input>, Output> {
    override suspend fun call(request: OperationRequest<Input>): Output {
        val builder = mapRequest(request.input)
        return inner.call(SdkHttpRequest(request.context, builder))
    }
}

private class StateService<Output> (
    private val inner: Service<SdkHttpRequest, Output>
) : Service<SdkHttpRequest, Output> {

    override suspend fun call(request: SdkHttpRequest): Output {
        return inner.call(request)
    }
}

private class FinalizeService<Output> (
    private val inner: Service<SdkHttpRequest, Output>
) : Service<SdkHttpRequest, Output> {

    override suspend fun call(request: SdkHttpRequest): Output {
        return inner.call(request)
    }
}

private class DeserializeService<Output>(
    private val inner: Service<SdkHttpRequest, HttpResponse>,
    private val mapResponse: suspend (HttpResponse) -> Output
) : Service<SdkHttpRequest, Output> {

    override suspend fun call(request: SdkHttpRequest): Output {
        // ensure the raw response is stashed in the context
        val rawResponse = inner.call(request)
        request.context[HttpOperationContext.HttpResponse] = rawResponse
        return mapResponse(rawResponse)
    }
}
