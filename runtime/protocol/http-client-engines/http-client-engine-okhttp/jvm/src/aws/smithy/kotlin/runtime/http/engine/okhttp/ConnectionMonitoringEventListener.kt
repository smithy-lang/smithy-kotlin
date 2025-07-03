/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.net.HostResolver
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.internal.closeQuietly
import okio.IOException
import okio.buffer
import okio.source
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * An [okhttp3.EventListener] implementation that monitors connections for remote closure.
 * This replaces the functionality previously provided by the now-internal [ConnectionListener].
 */
internal class ConnectionMonitoringEventListener(
    private val pollInterval: Duration,
) : EventListener(), Closeable {
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val monitors = ConcurrentHashMap<Int, Job>()

    /**
     * Close all active connection monitors.
     */
    fun close(): Unit = runBlocking {
        val monitorJob = requireNotNull(monitorScope.coroutineContext[Job]) {
            "Connection idle monitor scope cannot be cancelled because it does not have a job: $this"
        }
        monitorJob.cancelAndJoin()
    }

    private fun Call.callContext() =
        request()
            .tag(SdkRequestTag::class.java)
            ?.callContext
            ?: Dispatchers.IO

    // Cancel monitoring when a connection is acquired
    override fun connectionAcquired(call: Call, connection: Connection) {

        // Get connection ID
        val connId = System.identityHashCode(connection)

        // Non-locking map access is okay here because this code will only execute synchronously as part of a
        // `connectionAcquired` event and will be complete before any future `connectionReleased` event could fire for
        // the same connection.
        monitors.remove(connId)?.let { monitor ->
            val context = call.callContext()
            val logger = context.logger<ConnectionMonitoringEventListener>()
            logger.trace { "Cancel monitoring for $connId" }

            // Use `runBlocking` because this _must_ finish before OkHttp goes to use the connection
            val cancelTime = measureTime {
                runBlocking(context) { monitor.cancelAndJoin() }
            }

            logger.trace { "Monitoring canceled for $connId in $cancelTime" }
        }
    }

    // Start monitoring when a connection is released
    override fun connectionReleased(call: Call, connection: Connection) {

        val connId = System.identityHashCode(connection)
        val callContext = call.callContext()

        // Start monitoring
        val monitor = monitorScope.launch(CoroutineName("okhttp-conn-monitor-for-$connId")) {
            doMonitor(connection, callContext)
        }
        callContext.logger<ConnectionMonitoringEventListener>().trace { "Launched coroutine $monitor to monitor $connId" }

        // Non-locking map access is okay here because this code will only execute synchronously as part of a
        // `connectionReleased` event and will be complete before any future `connectionAcquired` event could fire for
        // the same connection.
        monitors[connId] = monitor
    }

    private suspend fun doMonitor(conn: Connection, callContext: CoroutineContext) {
        val logger = callContext.logger<ConnectionMonitoringEventListener>()
        val connId = System.identityHashCode(conn)

        val socket = conn.socket()
        val source = try {
            socket.source()
        } catch (_: SocketException) {
            logger.trace { "Socket for $connId closed before monitoring started. Skipping polling loop." }
            return
        }.buffer().peek()

        logger.trace { "Commence socket monitoring for $connId" }
        var resetTimeout = true
        val oldTimeout = socket.soTimeout

        try {
            socket.soTimeout = pollInterval.inWholeMilliseconds.toInt()

            while (coroutineContext.isActive) {
                try {
                    logger.trace { "Polling socket for $connId" }
                    source.readByte() // Blocking read; will take up to `pollInterval` time to complete
                } catch (_: SocketTimeoutException) {
                    logger.trace { "Socket still alive for $connId" }
                } catch (_: IOException) {
                    logger.trace { "Socket closed remotely for $connId" }
                    socket.closeQuietly()
                    resetTimeout = false
                    return
                }
            }

            logger.trace { "Monitoring coroutine has been cancelled. Ending polling loop." }
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to poll $connId. Ending polling loop. Connection may be unstable now." }
        } finally {
            if (resetTimeout) {
                logger.trace { "Attempting to reset soTimeout..." }
                try {
                    socket.soTimeout = oldTimeout
                    logger.trace { "soTimeout reset." }
                } catch (e: Throwable) {
                    logger.warn(e) { "Failed to reset socket timeout on $connId. Connection may be unstable now." }
                }
            }
        }
    }
}
