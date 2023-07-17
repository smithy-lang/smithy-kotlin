/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.HttpHandler
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.util.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlin.reflect.KClass

/**
 * A (Smithy) HTTP based operation.
 * @property execution Phases used to execute the operation request and get a response instance
 * @property context An [ExecutionContext] instance scoped to this operation
 * @property serializer The component responsible for serializing the input type `I` into an HTTP request builder
 * @property deserializer The component responsible for deserializing an HTTP response into the output type `O`
 * @property signer The component responsible for signing the request
 */
@InternalApi
public class SdkHttpOperation<I, O> internal constructor(
    public val execution: SdkOperationExecution<I, O>,
    public val context: ExecutionContext,
    internal val serializer: HttpSerialize<I>,
    internal val deserializer: HttpDeserialize<O>,
    internal val typeInfo: OperationTypeInfo,
) {
    init {
        val sdkRequestId = Uuid.random().toString()
        context[HttpOperationContext.SdkRequestId] = sdkRequestId
    }

    /**
     * Interceptors that will be executed as part of this operation. The difference between phases and interceptors
     * is the former is internal only whereas the latter is external customer facing. Middleware is also allowed to
     * suspend whereas interceptors are meant to be executed quickly.
     */
    public val interceptors: MutableList<HttpInterceptor> = mutableListOf()

    /**
     * Install a middleware into this operation's execution stack
     */
    public fun install(middleware: ModifyRequestMiddleware) { middleware.install(this) }

    // Convenience overloads for various types of middleware that target different phases
    // NOTE: Using install isn't strictly necessary, it's just a pattern for self registration
    public fun install(middleware: InitializeMiddleware<I, O>) { middleware.install(this) }
    public fun install(middleware: MutateMiddleware<O>) { middleware.install(this) }
    public fun install(middleware: ReceiveMiddleware) { middleware.install(this) }
    public fun install(middleware: InlineMiddleware<I, O>) { middleware.install(this) }

    @InternalApi
    public companion object {
        public inline fun <reified I, reified O> build(block: SdkHttpOperationBuilder<I, O>.() -> Unit): SdkHttpOperation<I, O> =
            SdkHttpOperationBuilder<I, O>(
                I::class,
                O::class,
            ).apply(block).build()
    }
}

/**
 * Gets the unique ID that identifies the active SDK request in this [ExecutionContext].
 */
@InternalApi
public val ExecutionContext.sdkRequestId: String
    get() = get(HttpOperationContext.SdkRequestId)

/**
 * Round trip an operation using the given [HttpHandler]
 */
@InternalApi
public suspend fun <I, O> SdkHttpOperation<I, O>.roundTrip(
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
public suspend fun <I, O, R> SdkHttpOperation<I, O>.execute(
    httpHandler: HttpHandler,
    input: I,
    block: suspend (O) -> R,
): R {
    val handler = execution.decorate(httpHandler, this)
    val request = OperationRequest(context, input)
    try {
        val output = handler.call(request)
        return block(output)
    } finally {
        context.cleanup()
    }
}

internal data class OperationTypeInfo(
    val inputType: KClass<*>,
    val outputType: KClass<*>,
)

@InternalApi
public class SdkHttpOperationBuilder<I, O> (
    private val inputType: KClass<*>,
    private val outputType: KClass<*>,
) {
    public var serializer: HttpSerialize<I>? = null
    public var deserializer: HttpDeserialize<O>? = null
    public val execution: SdkOperationExecution<I, O> = SdkOperationExecution()
    public val context: HttpOperationContext.Builder = HttpOperationContext.Builder()

    public fun build(): SdkHttpOperation<I, O> {
        val opSerializer = requireNotNull(serializer) { "SdkHttpOperation.serializer must not be null" }
        val opDeserializer = requireNotNull(deserializer) { "SdkHttpOperation.deserializer must not be null" }
        val typeInfo = OperationTypeInfo(inputType, outputType)
        return SdkHttpOperation(execution, context.build(), opSerializer, opDeserializer, typeInfo)
    }
}

/**
 * Configure HTTP operation context elements
 */
@InternalApi
public inline fun <I, O> SdkHttpOperationBuilder<I, O>.context(block: HttpOperationContext.Builder.() -> Unit) {
    context.apply(block)
}

private suspend fun ExecutionContext.cleanup() {
    // pull the raw response(s) out of the context and cleanup any resources
    getOrNull(HttpOperationContext.HttpCallList)?.forEach { it.complete() }

    // at this point everything associated with this single operation should be cleaned up
    coroutineContext.job.cancelAndJoin()
}
