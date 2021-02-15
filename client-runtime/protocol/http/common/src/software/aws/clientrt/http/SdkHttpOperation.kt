/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http

import software.aws.clientrt.client.ExecutionContext
import software.aws.clientrt.http.feature.HttpDeserialize
import software.aws.clientrt.http.feature.HttpSerialize
import software.aws.clientrt.http.request.OperationRequest
import software.aws.clientrt.util.InternalAPI

/**
 * A (Smithy) HTTP based operation.
 * @property execution Phases used to execute the operation request and get a response instance
 * @property context An [ExecutionContext] instance scoped to this operation
 */
@InternalAPI
data class SdkHttpOperation<I, O>(
    val execution: SdkOperationExecution<I, O>,
    val context: ExecutionContext,
    internal val serializer: HttpSerialize<I>,
    internal val deserializer: HttpDeserialize<O>,
) {

    private val features: MutableMap<FeatureKey<*>, Feature> = mutableMapOf()

    /**
     * Install a specific [feature] and optionally [configure] it.
     */
    fun <TConfig : Any, TFeature : Feature> install(
        feature: HttpClientFeatureFactory<TConfig, TFeature>,
        configure: TConfig.() -> Unit = {}
    ) {
        require(!features.contains(feature.key)) { "feature $feature has already been installed and configured" }
        val instance = feature.create(configure)
        features[feature.key] = instance
        instance.install(this)
    }

    companion object {
        fun <I, O> build(block: SdkHttpOperationBuilder<I, O>.() -> Unit): SdkHttpOperation<I, O> =
            SdkHttpOperationBuilder<I, O>().apply(block).build()
    }
}

/**
 * Round trip an operation using the given [HttpService]
 */
@InternalAPI
suspend fun <I, O> SdkHttpOperation<I, O>.roundTrip(
    httpService: HttpService,
    input: I,
): O = execute(httpService, input) { it }

/**
 * Make an operation request with the given [input] and return the result of executing [block] with the output.
 *
 * The response and any resources will remain open until the end of the [block]. This facilitates streaming
 * output responses where the underlying raw HTTP connection needs to remain open
 */
@InternalAPI
suspend fun <I, O, R> SdkHttpOperation<I, O>.execute(
    httpService: HttpService,
    input: I,
    block: suspend (O) -> R
): R {
    val service = execution.decorate(httpService, serializer, deserializer)
    val request = OperationRequest(context, input)
    val output = service.call(request)
    try {
        return block(output)
    } finally {
        // pull the raw response out of the context and cleanup any resources
        val httpResp = context.getOrNull(HttpOperationContext.HttpResponse)?.complete()
    }
}

@InternalAPI
class SdkHttpOperationBuilder<I, O> {
    var serializer: HttpSerialize<I>? = null
    var deserializer: HttpDeserialize<O>? = null
    val execution: SdkOperationExecution<I, O> = SdkOperationExecution()
    private val contextBuilder = HttpOperationContext.Builder()

    /**
     * Configure HTTP operation context elements
     */
    fun context(block: HttpOperationContext.Builder.() -> Unit) {
        contextBuilder.apply(block)
    }

    fun build(): SdkHttpOperation<I, O> {
        val opSerializer = requireNotNull(serializer) { "SdkHttpOperation.serializer must not be null" }
        val opDeserializer = requireNotNull(deserializer) { "SdkHttpOperation.deserializer must not be null" }
        return SdkHttpOperation(execution, contextBuilder.build(), opSerializer, opDeserializer)
    }
}
