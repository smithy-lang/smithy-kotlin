/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.telemetry.logging.logger
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Connection
import okhttp3.ConnectionListener
import okhttp3.ExperimentalOkHttpApi
import okhttp3.internal.closeQuietly
import okio.EOFException
import okio.buffer
import okio.source
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.measureTime

@OptIn(ExperimentalOkHttpApi::class)
internal class ConnectionIdleMonitor(val pollInterval: Duration) : ConnectionListener() {
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val monitors = ConcurrentHashMap<Connection, Job>()

    fun close(): Unit = runBlocking {
        monitors.values.forEach { it.cancelAndJoin() }
    }

    private fun Call.callContext() =
        request()
            .tag(SdkRequestTag::class.java)
            ?.callContext
            ?: Dispatchers.IO

    override fun connectionAcquired(connection: Connection, call: Call) {
        // Non-locking map access is okay here because this code will only execute synchronously as part of a
        // `connectionAcquired` event and will be complete before any future `connectionReleased` event could fire for
        // the same connection.
        monitors.remove(connection)?.let { monitor ->
            val context = call.callContext()
            val logger = context.logger<ConnectionIdleMonitor>()
            logger.trace { "Cancel monitoring for $connection" }

            // Use `runBlocking` because this _must_ finish before OkHttp goes to use the connection
            val cancelTime = measureTime {
                runBlocking(context) { monitor.cancelAndJoin() }
            }

            logger.trace { "Monitoring canceled for $connection in $cancelTime" }
        }
    }

    override fun connectionReleased(connection: Connection, call: Call) {
        val connId = System.identityHashCode(connection)
        val callContext = call.callContext()
        val monitor = monitorScope.launch(CoroutineName("okhttp-conn-monitor-for-$connId")) {
            doMonitor(connection, callContext)
        }
        callContext.logger<ConnectionIdleMonitor>().trace { "Launched coroutine $monitor to monitor $connection" }

        // Non-locking map access is okay here because this code will only execute synchronously as part of a
        // `connectionReleased` event and will be complete before any future `connectionAcquired` event could fire for
        // the same connection.
        monitors[connection] = monitor
    }

    private suspend fun doMonitor(conn: Connection, callContext: CoroutineContext) {
        val logger = callContext.logger<ConnectionIdleMonitor>()

        val socket = conn.socket()
        val source = try {
            socket.source()
        } catch (_: SocketException) {
            logger.trace { "Socket for $conn closed before monitoring started. Skipping polling loop." }
            return
        }.buffer().peek()

        logger.trace { "Commence socket monitoring for $conn" }
        var resetTimeout = true
        val oldTimeout = socket.soTimeout

        try {
            socket.soTimeout = pollInterval.inWholeMilliseconds.toInt()

            while (coroutineContext.isActive) {
                try {
                    logger.trace { "Polling socket for $conn" }
                    source.readByte() // Blocking read; will take up to `pollInterval` time to complete
                } catch (_: SocketTimeoutException) {
                    logger.trace { "Socket still alive for $conn" }
                } catch (_: EOFException) {
                    logger.trace { "Socket closed remotely for $conn" }
                    socket.closeQuietly()
                    resetTimeout = false
                    return
                }
            }

            logger.trace { "Monitoring coroutine has been cancelled. Ending polling loop." }
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to poll $conn. Ending polling loop. Connection may be unstable now." }
        } finally {
            if (resetTimeout) {
                logger.trace { "Attempting to reset soTimeout..." }
                try {
                    conn.socket().soTimeout = oldTimeout
                    logger.trace { "soTimeout reset." }
                } catch (e: Throwable) {
                    logger.warn(e) { "Failed to reset socket timeout on $conn. Connection may be unstable now." }
                }
            }
        }
    }
}
