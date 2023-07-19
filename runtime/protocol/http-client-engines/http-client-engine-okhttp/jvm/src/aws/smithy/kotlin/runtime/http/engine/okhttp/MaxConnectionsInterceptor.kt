/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Interceptor that enforces max connections setting.
 */
internal class MaxConnectionsInterceptor(
    private val limiter: ConnectionLimiter,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // LIFETIME: will be released by the event listener when the connectionReleased() callback is invoked
        // Acquire a lease for a connection for this request.
        limiter.acquireLease()
        return chain.proceed(chain.request())
    }
}

internal class ConnectionLimiter(
    private val maxConnections: Int,
    private val pool: ConnectionPool,
) {

    private val mutex = ReentrantLock(true)
    private val connectionsAvailable = mutex.newCondition()

    /**
     * Acquire a connection lease, waiting for capacity if [maxConnections] is reached.
     */
    fun acquireLease() {
        mutex.withLock {
            while (pool.connectionCount() >= maxConnections && pool.idleConnectionCount() == 0) {
                connectionsAvailable.await()
            }
        }

        // NOTE: we don't actually know if the current request will require a new connection or an idle one can be re-used.
        // Optimistically proceed as if we'll get an idle connection, we may end up exceeding max connections up to
        // whatever max idle connections setting is on the pool though
    }

    /**
     * Invoked when a connection is returned to the pool
     */
    fun connectionReleased() {
        mutex.withLock {
            connectionsAvailable.signal()
        }
    }
}
