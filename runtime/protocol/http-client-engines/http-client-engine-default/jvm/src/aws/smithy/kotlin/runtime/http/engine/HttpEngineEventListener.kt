/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.logging.Logger
import okhttp3.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

// TODO - propagate call metadata (e.g. sdkRequestId) using Request.tag. Requires more direct integration with okhttp

internal class HttpEngineEventListener(
    private val pool: ConnectionPool
) : EventListener() {
    private val logger = Logger.getLogger<HttpEngineEventListener>()

    override fun callStart(call: Call) {
        logger.trace { "call started" }
    }

    override fun callEnd(call: Call) {
        logger.trace { "call complete" }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        logger.trace(ioe) { "call failed" }
    }

    override fun canceled(call: Call) {
        logger.trace { "call cancelled" }
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        logger.trace { "starting connection: addr=$inetSocketAddress; proxy=$proxy" }
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        logger.trace { "connection established: addr=$inetSocketAddress; proxy=$proxy; protocol=$protocol" }
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        logger.trace(ioe) { "connect failed: addr=$inetSocketAddress; proxy=$proxy; protocol=$protocol" }
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        logger.trace { "connection acquired: conn=$connection; connPool: total=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}" }
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        logger.trace { "connection released: conn=$connection; connPool: total=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}" }
    }

    override fun dnsStart(call: Call, domainName: String) {
        logger.trace { "dns query: domain=$domainName" }
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        logger.trace { "dns resolved: domain=$domainName; records=$inetAddressList" }
    }

    override fun proxySelectStart(call: Call, url: HttpUrl) {
        logger.trace { "proxy select start: url=$url" }
    }

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) {
        logger.trace { "proxy select end: url=$url" }
    }

    override fun requestBodyStart(call: Call) {
        logger.trace { "sending request body" }
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        logger.trace { "finished sending request body: bytesSent=$byteCount" }
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        logger.trace(ioe) { "request failed" }
    }

    override fun requestHeadersStart(call: Call) {
        logger.trace { "sending request headers" }
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        logger.trace { "finished sending request headers" }
    }

    override fun responseBodyStart(call: Call) {
        logger.trace { "response body available" }
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        logger.trace { "response body finished: bytesConsumed=$byteCount" }
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        logger.trace(ioe) { "response failed" }
    }

    override fun responseHeadersStart(call: Call) {
        logger.trace { "response headers start" }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val contentLength = response.body?.contentLength()
        logger.trace { "response headers end: contentLengthHeader=$contentLength" }
    }

    override fun secureConnectStart(call: Call) {
        logger.trace { "initiating TLS connection" }
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        logger.trace { "TLS connect end: handshake=$handshake" }
    }

    // NOTE: we don't configure a cache and should never get the rest of these events,
    // seeing these messages logged means we configured something wrong

    override fun satisfactionFailure(call: Call, response: Response) {
        logger.trace { "cache satisfaction failure" }
    }

    override fun cacheConditionalHit(call: Call, cachedResponse: Response) {
        logger.trace { "cache conditional hit" }
    }

    override fun cacheHit(call: Call, response: Response) {
        logger.trace { "cache hit" }
    }

    override fun cacheMiss(call: Call) {
        logger.trace { "cache miss" }
    }
}
