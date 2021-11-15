/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.http.Feature
import aws.smithy.kotlin.runtime.http.FeatureKey
import aws.smithy.kotlin.runtime.http.HttpClientFeatureFactory
import aws.smithy.kotlin.runtime.http.HttpHandler
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.Uuid
import aws.smithy.kotlin.runtime.util.get

/**
 * A (Smithy) HTTP based operation.
 * @property execution Phases used to execute the operation request and get a response instance
 * @property context An [ExecutionContext] instance scoped to this operation
 */
@OptIn(Uuid.WeakRng::class)
@InternalApi
class SdkHttpOperation<I, O>(
    val execution: SdkOperationExecution<I, O>,
    val context: ExecutionContext,
    internal val serializer: HttpSerialize<I>,
    internal val deserializer: HttpDeserialize<O>,
) {

    private val features: MutableMap<FeatureKey<*>, Feature> = mutableMapOf()

    init {
        val sdkRequestId = Uuid.random().toString()
        context[HttpOperationContext.SdkRequestId] = sdkRequestId
        context[HttpOperationContext.LoggingContext] = mapOf(
            "sdkRequestId" to sdkRequestId,
            "service" to context[SdkClientOption.ServiceName],
            "operation" to context[SdkClientOption.OperationName],
        )
    }

    // FIXME - remove the feature concept
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

    /**
     * Install a middleware into this operation's execution stack
     */
    fun install(middleware: AutoInstall<I, O>) { middleware.install(this) }
    fun install(middleware: ModifyRequestMiddleware) { middleware.install(this) }

    companion object {
        inline fun <I, O> build(block: SdkHttpOperationBuilder<I, O>.() -> Unit): SdkHttpOperation<I, O> =
            SdkHttpOperationBuilder<I, O>().apply(block).build()
    }
}

/**
 * Round trip an operation using the given [HttpHandler]
 */
@InternalApi
suspend fun <I, O> SdkHttpOperation<I, O>.roundTrip(
    httpHandler: HttpHandler,
    input: I,
): O = execute(httpHandler, input) { it }

/**
 * Make an operation request with the given [input] and return the result of executing [block] with the output.
 *
 * The response and any resources will remain open until the end of the [block]. This facilitates streaming
 * output responses where the underlying raw HTTP connection needs to remain open
 */
@InternalApi
suspend fun <I, O, R> SdkHttpOperation<I, O>.execute(
    httpHandler: HttpHandler,
    input: I,
    block: suspend (O) -> R
): R {
    val handler = execution.decorate(httpHandler, serializer, deserializer)
    val request = OperationRequest(context, input)
    try {
        val output = handler.call(request)
        return block(output)
    } finally {
        // pull the raw response(s) out of the context and cleanup any resources
        val httpResp = context.getOrNull(HttpOperationContext.HttpCallList)?.forEach { it.complete() }
    }
}

@InternalApi
class SdkHttpOperationBuilder<I, O> {
    var serializer: HttpSerialize<I>? = null
    var deserializer: HttpDeserialize<O>? = null
    val execution: SdkOperationExecution<I, O> = SdkOperationExecution()
    val context = HttpOperationContext.Builder()

    fun build(): SdkHttpOperation<I, O> {
        val opSerializer = requireNotNull(serializer) { "SdkHttpOperation.serializer must not be null" }
        val opDeserializer = requireNotNull(deserializer) { "SdkHttpOperation.deserializer must not be null" }
        return SdkHttpOperation(execution, context.build(), opSerializer, opDeserializer)
    }
}
/**
 * Configure HTTP operation context elements
 */
inline fun <I, O> SdkHttpOperationBuilder<I, O>.context(block: HttpOperationContext.Builder.() -> Unit) = context.apply(block)
