/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetricAttributes
import aws.smithy.kotlin.runtime.http.engine.internal.HttpClientMetrics
import aws.smithy.kotlin.runtime.net.HostResolver
import aws.smithy.kotlin.runtime.net.toHostAddress
import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.logging.MessageSupplier
import aws.smithy.kotlin.runtime.telemetry.logging.getLogger
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.telemetry.metrics.decrement
import aws.smithy.kotlin.runtime.telemetry.metrics.increment
import aws.smithy.kotlin.runtime.telemetry.telemetryProvider
import aws.smithy.kotlin.runtime.telemetry.trace.SpanStatus
import aws.smithy.kotlin.runtime.telemetry.trace.recordException
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.get
import okhttp3.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val TELEMETRY_SCOPE = "aws.smithy.kotlin.runtime.http.engine.okhttp"

@OptIn(ExperimentalTime::class)
internal class HttpEngineEventListener(
    private val pool: ConnectionPool,
    private val hr: HostResolver,
    call: Call,
) : EventListener() {
    private val provider: TelemetryProvider = call.request().tag<SdkRequestTag>()?.callContext?.telemetryProvider ?: TelemetryProvider.None
    private val traceSpan = provider.tracerProvider
        .getOrCreateTracer(TELEMETRY_SCOPE)
        .createSpan("HTTP")
    private val logger = call.request().tag<SdkRequestTag>()?.callContext?.logger<OkHttpEngine>() ?: LoggerProvider.None.getLogger<OkHttpEngine>()

    private val metrics = HttpClientMetrics(TELEMETRY_SCOPE, provider)

    // FIXME - need idle/warm connection usage but requires tracking delta? Better suited for async UpDownCounter...
    init {
        // listener is created at same time as a call, the call is then enqueued until dispatcher can execute it
        metrics.requests.increment(HttpClientMetricAttributes.QueuedRequest)
    }

    // see https://square.github.io/okhttp/features/events/#eventlistener for flow
    private var connectStart: TimeMark? = null

    private inline fun trace(crossinline msg: MessageSupplier) {
        logger.trace { msg() }
    }

    private inline fun trace(throwable: Throwable, crossinline msg: MessageSupplier) {
        logger.trace(throwable) { msg() }
    }

    override fun callStart(call: Call) {
        connectStart = TimeSource.Monotonic.markNow()
        metrics.requests.decrement(HttpClientMetricAttributes.QueuedRequest)
        metrics.requests.increment(HttpClientMetricAttributes.InFlightRequest)
        trace { "call started" }
    }

    override fun callEnd(call: Call) {
        metrics.requests.decrement(HttpClientMetricAttributes.InFlightRequest)
        trace { "call complete" }
        traceSpan.close()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        metrics.requests.decrement(HttpClientMetricAttributes.InFlightRequest)
        trace(ioe) { "call failed" }
        traceSpan.recordException(ioe, true)
        traceSpan.setStatus(SpanStatus.ERROR)
        traceSpan.close()
    }

    override fun canceled(call: Call) = trace { "call cancelled" }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
        trace { "starting connection: addr=$inetSocketAddress; proxy=$proxy" }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) =
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
        metrics.connectionUsage.increment(HttpClientMetricAttributes.AcquiredConnection)
        connectStart?.let {
            metrics.connectionAcquireDuration.record(it.elapsedNow().inWholeMilliseconds)
        }

        val connId = System.identityHashCode(connection)
        trace { "connection acquired: conn(id=$connId)=$connection; connPool: total=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}" }
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        metrics.connectionUsage.decrement(HttpClientMetricAttributes.AcquiredConnection)
        val connId = System.identityHashCode(connection)
        trace { "connection released: conn(id=$connId)=$connection; connPool: total=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}" }
    }

    override fun dnsStart(call: Call, domainName: String) = trace { "dns query: domain=$domainName" }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) =
        trace { "dns resolved: domain=$domainName; records=$inetAddressList" }

    override fun proxySelectStart(call: Call, url: HttpUrl) = trace { "proxy select start: url=$url" }

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) =
        trace { "proxy select end: url=$url; proxies=$proxies" }

    override fun requestBodyStart(call: Call) = trace { "sending request body" }

    override fun requestBodyEnd(call: Call, byteCount: Long) =
        trace { "finished sending request body: bytesSent=$byteCount" }

    override fun requestFailed(call: Call, ioe: IOException) = trace(ioe) { "request failed" }

    override fun requestHeadersStart(call: Call) = trace { "sending request headers" }

    override fun requestHeadersEnd(call: Call, request: Request) = trace { "finished sending request headers" }

    override fun responseBodyStart(call: Call) = trace { "response body available" }

    override fun responseBodyEnd(call: Call, byteCount: Long) =
        trace { "response body finished: bytesConsumed=$byteCount" }

    override fun responseFailed(call: Call, ioe: IOException) = trace(ioe) { "response failed" }

    override fun responseHeadersStart(call: Call) = trace { "response headers start" }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val contentLength = response.body.contentLength()
        trace { "response headers end: contentLengthHeader=$contentLength" }
    }

    override fun secureConnectStart(call: Call) = trace { "initiating TLS connection" }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) = trace { "TLS connect end: handshake=$handshake" }

    // NOTE: we don't configure a cache and should never get the rest of these events,
    // seeing these messages logged means we configured something wrong

    override fun satisfactionFailure(call: Call, response: Response) = trace { "cache satisfaction failure" }

    override fun cacheConditionalHit(call: Call, cachedResponse: Response) = trace { "cache conditional hit" }

    override fun cacheHit(call: Call, response: Response) = trace { "cache hit" }

    override fun cacheMiss(call: Call) = trace { "cache miss" }
}
