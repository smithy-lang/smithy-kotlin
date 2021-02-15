/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http

import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.http.feature.DeserializationProvider
import software.aws.clientrt.http.feature.SerializationProvider
import software.aws.clientrt.http.middleware.*
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.OperationRequest
import software.aws.clientrt.http.request.SdkHttpRequest
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.http.response.OperationResponse
import software.aws.clientrt.serde.SerdeProvider

typealias HttpService = Service<HttpRequestBuilder, HttpResponse>

// FIXME - wtf to call this
class SdkOperationExecution<Request, Response>{

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

// FIXME - rename SdkHttpOperation?
class Operation<I, O>(
    val execution: SdkOperationExecution<I, O>,
    val serializer: HttpSerialize<I>,
    val deserializer: HttpDeserialize<O>,
)

suspend fun<I, O> Operation<I,O>.roundTrip(
    httpService: HttpService,
    context: ExecutionContext,
    input: I,
): O = execute(httpService, context, input) { it }

suspend fun<I, O, R> Operation<I,O>.execute(
    httpService: HttpService,
    context: ExecutionContext,
    input: I,
    block: suspend (O) -> R
): R {
    val service = execution.decorate(httpService, serializer, deserializer)
    val request = OperationRequest(context, input)
    val output = service.call(request)
    try {
        return block(output)
    }finally {
        // pull the raw response out of the context and cleanup any resources
        val httpResp = context.getOrNull(SdkHttpOperation.HttpResponse)?.complete()
    }
}

internal fun<Request, Response> SdkOperationExecution<Request, Response>.decorate(
    service: HttpService,
    serializer: HttpSerialize<Request>,
    deserializer: HttpDeserialize<Response>,
): Service<OperationRequest<Request>, Response> {

    val inner = MapRequest(service) { sdkRequest: SdkHttpRequest ->
        sdkRequest.request
    }

    val receiveService = decorate(inner, receive)
    val deserializeService = deserializer.decorate(receiveService)
    val finalizeService = decorate(FinalizeService(deserializeService), finalize)
    val stateService = decorate(StateService(finalizeService), state)
    val serializeService = serializer.decorate(stateService)
    return decorate(InitializeService(serializeService), initialize)
}



private fun<I, O> HttpSerialize<I>.decorate(
    inner: Service<SdkHttpRequest, O>
): Service<OperationRequest<I>, O> {
    val mapRequest: suspend (input: I) -> HttpRequestBuilder = { input ->
        HttpRequestBuilder().also{
            serialize(it, input)
        }
    }
    return SerializeService(inner, mapRequest)
}

private fun<O> HttpDeserialize<O>.decorate(
    inner: Service<SdkHttpRequest, HttpResponse>,
): Service<SdkHttpRequest, O> {
    return DeserializeService(inner, ::deserialize)
}


interface HttpSerialize<T> {
    suspend fun serialize(builder: HttpRequestBuilder, input: T)
}

interface HttpDeserialize<T> {
    suspend fun deserialize(response: HttpResponse): T
}

// ??
//typealias HttpSerialize2<T> = suspend (input: T, builder: HttpRequestBuilder, provider: SerializationProvider) -> Unit




class OperationInput
class OperationOutput
class OperationInputSerializer(val provider: SerializationProvider): HttpSerialize<OperationInput> {
    override suspend fun serialize(builder: HttpRequestBuilder, input: OperationInput) {
        println("serialize operation input")
    }
}

class OperationOutputDeserializer(val provider: DeserializationProvider): HttpDeserialize<OperationOutput> {
    override suspend fun deserialize(response: HttpResponse): OperationOutput {
        println("deserializer operation output")
        return OperationOutput()
    }
}


suspend fun exampleOperation(httpService: HttpService, serde: SerdeProvider, input: OperationInput): OperationOutput {

    val context = SdkHttpOperation.build {
        operationName = "foo"
    }

    // mergeServiceDefaults(context)

    // TODO - would like this to just be SdkHttpOperation...then we can have operation.roundTrip()
    val execution = SdkOperationExecution<OperationInput, OperationOutput>()
//    registerDefaultFeatures(execution)
    // execution.configure {
    //     install(feature) { }
    //     install(feature) { }
    //     install(feature) { }
    // }

    val serializer = OperationInputSerializer(serde::serializer)
    val deserializer = OperationOutputDeserializer(serde::deserializer)
    val operation = Operation(execution, serializer, deserializer)
    return operation.roundTrip(httpService, context, input)

}

//class FooMiddleware<Input, Output>: Middleware<Input, Output> {
//    override suspend fun <S : Service<Input, Output>> handle(request: Input, next: S): Output {
//        TODO("Not yet implemented")
//    }
//}


// internal glue

private class InitializeService<Input, Output>(
    private val inner: Service<Input, Output>
): Service<Input, Output>
{
    override suspend fun call(request: Input): Output {
        return inner.call(request)
    }
}

private class SerializeService<Input, Output> (
    private val inner: Service<SdkHttpRequest, Output>,
    private val mapRequest: suspend (Input) -> HttpRequestBuilder
): Service<OperationRequest<Input>, Output> {
    override suspend fun call(request: OperationRequest<Input>): Output {
        val builder = mapRequest(request.input)
        return inner.call(SdkHttpRequest(request.context, builder))
    }

}

private class StateService<Output> (
    private val inner: Service<SdkHttpRequest, Output>
): Service<SdkHttpRequest, Output> {

    override suspend fun call(request: SdkHttpRequest): Output {
        return inner.call(request)
    }
}

private class FinalizeService<Output> (
    private val inner: Service<SdkHttpRequest, Output>
): Service<SdkHttpRequest, Output> {

    override suspend fun call(request: SdkHttpRequest): Output {
        return inner.call(request)
    }
}

private class DeserializeService<Output>(
    private val inner: Service<SdkHttpRequest, HttpResponse>,
    private val mapResponse: suspend (HttpResponse) -> Output
): Service<SdkHttpRequest, Output> {

    override suspend fun call(request: SdkHttpRequest): Output {
        // ensure the raw response is stashed in the context
        val rawResponse = inner.call(request)
        request.context[SdkHttpOperation.HttpResponse] = rawResponse
        return mapResponse(rawResponse)
    }
}

