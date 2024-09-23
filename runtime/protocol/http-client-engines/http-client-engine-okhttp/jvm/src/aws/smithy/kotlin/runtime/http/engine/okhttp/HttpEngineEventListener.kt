/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.engine.EngineAttributes
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.net.HostResolver
import aws.smithy.kotlin.runtime.net.toHostAddress
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.logging.MessageSupplier
import aws.smithy.kotlin.runtime.telemetry.logging.getLogger
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.telemetry.metrics.recordSeconds
import aws.smithy.kotlin.runtime.telemetry.telemetryProvider
import aws.smithy.kotlin.runtime.telemetry.trace.SpanStatus
import aws.smithy.kotlin.runtime.telemetry.trace.recordException
import okhttp3.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal const val TELEMETRY_SCOPE = "aws.smithy.kotlin.runtime.http.engine.okhttp"

// see https://square.github.io/okhttp/features/events/#eventlistener for example callback flow
@OptIn(ExperimentalApi::class)
@InternalApi
public class HttpEngineEventListener(
    private val pool: ConnectionPool,
    private val hr: HostResolver,
    private val dispatcher: Dispatcher,
    private val metrics: HttpClientMetrics,
    call: Call,
) : EventListener() {
    private val provider: TelemetryProvider = call.request().tag(SdkRequestTag::class.java)?.callContext?.telemetryProvider ?: TelemetryProvider.None
    private val traceSpan = provider.tracerProvider
        .getOrCreateTracer(TELEMETRY_SCOPE)
        .createSpan("HTTP")

    private val logger = call.request().tag(SdkRequestTag::class.java)?.callContext?.logger<OkHttpEngine>() ?: LoggerProvider.None.getLogger<OkHttpEngine>()

    // callStart() is invoked immediately when enqueued, next success phase is either dnsStart() or connectionAcquired()
    //  see https://github.com/square/okhttp/blob/7c92ed0879477eddb2fce6b4066d151525d5687f/okhttp/src/jvmMain/kotlin/okhttp3/internal/connection/RealCall.kt#L167-L175
    private var callTimeStart: TimeMark? = null
    private var signaledQueuedDuration = false
    private var queuedDuration = 0.seconds

    private var signaledConnectAcquireDuration = false
    private var dnsStartTime: TimeMark? = null

    private var requestTimeEnd: TimeMark? = null

    private inline fun trace(crossinline msg: MessageSupplier) {
        logger.trace { msg() }
    }

    private inline fun trace(throwable: Throwable, crossinline msg: MessageSupplier) {
        logger.trace(throwable) { msg() }
    }

    // Method ordering taken from https://square.github.io/okhttp/features/events/#eventlistener

    override fun callStart(call: Call) {
        val now = TimeSource.Monotonic.markNow()
        callTimeStart = now
        metrics.queuedRequests = dispatcher.queuedCallsCount().toLong()
        metrics.inFlightRequests = dispatcher.runningCallsCount().toLong()
        trace { "call started" }
    }

    override fun dnsStart(call: Call, domainName: String) {
        dnsStartTime = TimeSource.Monotonic.markNow()
        if (!signaledQueuedDuration) {
            queuedDuration = checkNotNull(callTimeStart).elapsedNow()
            metrics.requestsQueuedDuration.recordSeconds(queuedDuration)
            signaledQueuedDuration = true
        }
        trace { "dns query: domain=$domainName" }
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>): Unit =
        trace { "dns resolved: domain=$domainName; records=$inetAddressList" }

    override fun proxySelectStart(call: Call, url: HttpUrl): Unit = trace { "proxy select start: url=$url" }

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>): Unit =
        trace { "proxy select end: url=$url; proxies=$proxies" }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy): Unit =
        trace { "starting connection: addr=$inetSocketAddress; proxy=$proxy" }

    override fun secureConnectStart(call: Call): Unit = trace { "initiating TLS connection" }

    override fun secureConnectEnd(call: Call, handshake: Handshake?): Unit = trace { "TLS connect end: handshake=$handshake" }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?): Unit =
        trace { "connection established: addr=$inetSocketAddress; proxy=$proxy; protocol=$protocol" }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        trace(ioe) { "connect failed: addr=$inetSocketAddress; proxy=$proxy; protocol=$protocol" }
        hr.reportFailure(inetSocketAddress.address.toHostAddress())
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        metrics.acquiredConnections = pool.connectionCount().toLong()
        metrics.idleConnections = pool.idleConnectionCount().toLong()

        val callStarted = checkNotNull(callTimeStart)
        if (!signaledQueuedDuration) {
            signaledQueuedDuration = true
            queuedDuration = callStarted.elapsedNow()
            metrics.requestsQueuedDuration.recordSeconds(queuedDuration)
        }

        if (!signaledConnectAcquireDuration) {
            signaledConnectAcquireDuration = true

            val connectAcquireDuration = if (dnsStartTime != null) {
                dnsStartTime!!.elapsedNow()
            } else {
                callStarted.elapsedNow() - queuedDuration
            }
            metrics.connectionAcquireDuration.recordSeconds(connectAcquireDuration)
        }

        val connId = System.identityHashCode(connection)
        trace { "connection acquired: conn(id=$connId)=$connection; connPool: total=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}" }
    }

    override fun requestHeadersStart(call: Call): Unit = trace { "sending request headers" }

    override fun requestHeadersEnd(call: Call, request: Request) {
        if (request.body == null) {
            requestTimeEnd = TimeSource.Monotonic.markNow()
        }

        trace { "finished sending request headers" }
    }

    override fun requestBodyStart(call: Call): Unit = trace { "sending request body" }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        requestTimeEnd = TimeSource.Monotonic.markNow()
        trace { "finished sending request body: bytesSent=$byteCount" }
    }

    override fun requestFailed(call: Call, ioe: IOException): Unit = trace(ioe) { "request failed" }

    override fun responseHeadersStart(call: Call) {
        requestTimeEnd?.elapsedNow()?.let { ttfb ->
            metrics.timeToFirstByteDuration.recordSeconds(ttfb)
            call.request().tag(SdkRequestTag::class.java)?.execContext?.set(EngineAttributes.TimeToFirstByte, ttfb)
        }
        trace { "response headers start" }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val contentLength = response.body?.contentLength()
        trace { "response headers end: contentLengthHeader=$contentLength" }
    }

    override fun responseBodyStart(call: Call): Unit = trace { "response body available" }

    override fun responseBodyEnd(call: Call, byteCount: Long): Unit =
        trace { "response body finished: bytesConsumed=$byteCount" }

    override fun responseFailed(call: Call, ioe: IOException): Unit = trace(ioe) { "response failed" }

    override fun connectionReleased(call: Call, connection: Connection) {
        metrics.acquiredConnections = pool.connectionCount().toLong()
        metrics.idleConnections = pool.idleConnectionCount().toLong()
        val connId = System.identityHashCode(connection)
        trace { "connection released: conn(id=$connId)=$connection; connPool: total=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}" }
    }

    override fun callEnd(call: Call) {
        metrics.queuedRequests = dispatcher.queuedCallsCount().toLong()
        metrics.inFlightRequests = dispatcher.runningCallsCount().toLong()
        trace { "call complete" }
        traceSpan.close()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        metrics.queuedRequests = dispatcher.queuedCallsCount().toLong()
        metrics.inFlightRequests = dispatcher.runningCallsCount().toLong()
        trace(ioe) { "call failed" }
        traceSpan.recordException(ioe, true)
        traceSpan.setStatus(SpanStatus.ERROR)
        traceSpan.close()
    }

    override fun canceled(call: Call): Unit = trace { "call cancelled" }

    // NOTE: we don't configure a cache and should never get the rest of these events,
    // seeing these messages logged means we configured something wrong

    override fun satisfactionFailure(call: Call, response: Response): Unit = trace { "cache satisfaction failure" }

    override fun cacheConditionalHit(call: Call, cachedResponse: Response): Unit = trace { "cache conditional hit" }

    override fun cacheHit(call: Call, response: Response): Unit = trace { "cache hit" }

    override fun cacheMiss(call: Call): Unit = trace { "cache miss" }
}
