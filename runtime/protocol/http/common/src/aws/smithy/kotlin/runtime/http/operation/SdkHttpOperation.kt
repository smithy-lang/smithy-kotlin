/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.client.SdkClientOption
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
public class SdkHttpOperation<I, O>(
    public val execution: SdkOperationExecution<I, O>,
    public val context: ExecutionContext,
    internal val serializer: HttpSerialize<I>,
    internal val deserializer: HttpDeserialize<O>,
) {

    init {
        val sdkRequestId = Uuid.random().toString()
        context[HttpOperationContext.SdkRequestId] = sdkRequestId
        context[HttpOperationContext.LoggingContext] = mapOf(
            "sdkRequestId" to sdkRequestId,
            "service" to context[SdkClientOption.ServiceName],
            "operation" to context[SdkClientOption.OperationName],
        )
    }

    /**
     * Install a middleware into this operation's execution stack
     */
    public fun install(middleware: ModifyRequestMiddleware) { middleware.install(this) }

    // Convenience overloads for various types of middleware that target different phases
    // NOTE: Using install isn't strictly necessary, it's just a pattern for self registration
    public fun install(middleware: InitializeMiddleware<I, O>) { middleware.install(this) }
    public fun install(middleware: MutateMiddleware<O>) { middleware.install(this) }
    public fun install(middleware: FinalizeMiddleware<O>) { middleware.install(this) }
    public fun install(middleware: ReceiveMiddleware) { middleware.install(this) }
    public fun install(middleware: InlineMiddleware<I, O>) { middleware.install(this) }

    public companion object {
        public inline fun <I, O> build(block: SdkHttpOperationBuilder<I, O>.() -> Unit): SdkHttpOperation<I, O> =
            SdkHttpOperationBuilder<I, O>().apply(block).build()
    }
}

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
    block: suspend (O) -> R
): R {
    val handler = execution.decorate(httpHandler, serializer, deserializer)
    val request = OperationRequest(context, input)
    try {
        val output = handler.call(request)
        return block(output)
    } finally {
        // pull the raw response(s) out of the context and cleanup any resources
        context.getOrNull(HttpOperationContext.HttpCallList)?.forEach { it.complete() }
    }
}

@InternalApi
public class SdkHttpOperationBuilder<I, O> {
    public var serializer: HttpSerialize<I>? = null
    public var deserializer: HttpDeserialize<O>? = null
    public val execution: SdkOperationExecution<I, O> = SdkOperationExecution()
    public val context: HttpOperationContext.Builder = HttpOperationContext.Builder()

    public fun build(): SdkHttpOperation<I, O> {
        val opSerializer = requireNotNull(serializer) { "SdkHttpOperation.serializer must not be null" }
        val opDeserializer = requireNotNull(deserializer) { "SdkHttpOperation.deserializer must not be null" }
        return SdkHttpOperation(execution, context.build(), opSerializer, opDeserializer)
    }
}
/**
 * Configure HTTP operation context elements
 */
public inline fun <I, O> SdkHttpOperationBuilder<I, O>.context(block: HttpOperationContext.Builder.() -> Unit) {
    context.apply(block)
}
