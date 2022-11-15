/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.tracing.NoOpTraceSpan
import aws.smithy.kotlin.runtime.tracing.logger
import okhttp3.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

internal class HttpEngineEventListener(private val pool: ConnectionPool, call: Call) : EventListener() {
    private val traceSpan = call.request().tag<SdkRequestTag>()?.traceSpan?.child("HTTP") ?: NoOpTraceSpan
    private val logger = traceSpan.logger<HttpEngineEventListener>()

    private inline fun trace(crossinline msg: () -> Any) {
        logger.trace { msg() }
    }

    private inline fun trace(throwable: Throwable, crossinline msg: () -> Any) {
        logger.trace(throwable) { msg() }
    }

    override fun callStart(call: Call) = trace { "call started" }

    override fun callEnd(call: Call) {
        trace { "call complete" }
        traceSpan.close()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        trace(ioe) { "call failed" }
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
    ) = trace(ioe) { "connect failed: addr=$inetSocketAddress; proxy=$proxy; protocol=$protocol" }

    override fun connectionAcquired(call: Call, connection: Connection) {
        val connId = System.identityHashCode(connection)
        trace { "connection acquired: conn(id=$connId)=$connection; connPool: total=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}" }
    }

    override fun connectionReleased(call: Call, connection: Connection) {
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
