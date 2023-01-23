/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Functionality a real HTTP client must provide.
 */
public interface HttpClientEngine : CoroutineScope {
    /**
     * Execute a single HTTP request and return the response.
     * Consumers *MUST* call `HttpCall.complete()` when finished processing the response
     */
    public suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall
}

/**
 * An [HttpClientEngine] with [Closeable] resources. Users SHOULD call [close] when done with the engine to ensure
 * any held resources are properly released.
 */
public interface CloseableHttpClientEngine : HttpClientEngine, Closeable

/**
 * Base class that SDK [HttpClientEngine]s SHOULD inherit from rather than implementing directly.
 */
@InternalApi
public abstract class HttpClientEngineBase(engineName: String) : CloseableHttpClientEngine {
    // why SupervisorJob? because failure of individual requests should not affect other requests or the overall engine
    override val coroutineContext: CoroutineContext = SupervisorJob() + CoroutineName("http-client-engine-$engineName-context")
    private val closed = atomic(false)

    final override fun close() {
        if (!closed.compareAndSet(false, true)) return

        val job = coroutineContext[Job] as? CompletableJob ?: return

        job.complete()
        job.invokeOnCompletion {
            shutdown()
        }
    }

    /**
     * Shutdown the engine completely and release resources. When this is invoked the engine
     * is guaranteed no new requests will come and that all in-flight requests have completed
     */
    protected open fun shutdown() { }
}

/**
 * Indicates an [HttpClientEngine] is closed already and no further requests should be initiated.
 */
public class HttpClientEngineClosedException(override val cause: Throwable? = null) : ClientException("HttpClientEngine already closed")
