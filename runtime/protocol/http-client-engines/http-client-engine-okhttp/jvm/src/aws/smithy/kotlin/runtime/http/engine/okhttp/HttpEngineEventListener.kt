/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.logging.Logger
import aws.smithy.kotlin.runtime.util.get
import okhttp3.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

internal class HttpEngineEventListener(
    private val pool: ConnectionPool
) : EventListener() {
    private val logger = Logger.getLogger<HttpEngineEventListener>()

    private inline fun traceCall(call: Call, crossinline msg: () -> Any) {
        val sdkRequestTag = call.request().tag<SdkRequestTag>()
        val sdkRequestId = sdkRequestTag?.execContext?.getOrNull(HttpOperationContext.SdkRequestId)
        logger.trace { "[sdkRequestId=$sdkRequestId] ${msg()}" }
    }

    private inline fun traceCall(call: Call, throwable: Throwable, crossinline msg: () -> Any) {
        val sdkRequestTag = call.request().tag<SdkRequestTag>()
        val sdkRequestId = sdkRequestTag?.execContext?.get(HttpOperationContext.SdkRequestId)
        logger.trace(throwable) { "[sdkRequestId=$sdkRequestId] ${msg()}" }
    }

    override fun callStart(call: Call) {
        traceCall(call) { "call started" }
    }

    override fun callEnd(call: Call) {
        traceCall(call) { "call complete" }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        traceCall(call, ioe) { "call failed" }
    }

    override fun canceled(call: Call) {
        traceCall(call) { "call cancelled" }
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        traceCall(call) { "starting connection: addr=$inetSocketAddress; proxy=$proxy" }
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        traceCall(call) { "connection established: addr=$inetSocketAddress; proxy=$proxy; protocol=$protocol" }
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        traceCall(call, ioe) { "connect failed: addr=$inetSocketAddress; proxy=$proxy; protocol=$protocol" }
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        val connId = System.identityHashCode(connection)
        traceCall(call) { "connection acquired: conn(id=$connId)=$connection; connPool: total=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}" }
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        val connId = System.identityHashCode(connection)
        traceCall(call) { "connection released: conn(id=$connId)=$connection; connPool: total=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}" }
    }

    override fun dnsStart(call: Call, domainName: String) {
        traceCall(call) { "dns query: domain=$domainName" }
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        traceCall(call) { "dns resolved: domain=$domainName; records=$inetAddressList" }
    }

    override fun proxySelectStart(call: Call, url: HttpUrl) {
        traceCall(call) { "proxy select start: url=$url" }
    }

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) {
        traceCall(call) { "proxy select end: url=$url" }
    }

    override fun requestBodyStart(call: Call) {
        traceCall(call) { "sending request body" }
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        traceCall(call) { "finished sending request body: bytesSent=$byteCount" }
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        traceCall(call, ioe) { "request failed" }
    }

    override fun requestHeadersStart(call: Call) {
        traceCall(call) { "sending request headers" }
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        traceCall(call) { "finished sending request headers" }
    }

    override fun responseBodyStart(call: Call) {
        traceCall(call) { "response body available" }
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        traceCall(call) { "response body finished: bytesConsumed=$byteCount" }
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        traceCall(call, ioe) { "response failed" }
    }

    override fun responseHeadersStart(call: Call) {
        traceCall(call) { "response headers start" }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val contentLength = response.body.contentLength()
        traceCall(call) { "response headers end: contentLengthHeader=$contentLength" }
    }

    override fun secureConnectStart(call: Call) {
        traceCall(call) { "initiating TLS connection" }
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        traceCall(call) { "TLS connect end: handshake=$handshake" }
    }

    // NOTE: we don't configure a cache and should never get the rest of these events,
    // seeing these messages logged means we configured something wrong

    override fun satisfactionFailure(call: Call, response: Response) {
        traceCall(call) { "cache satisfaction failure" }
    }

    override fun cacheConditionalHit(call: Call, cachedResponse: Response) {
        traceCall(call) { "cache conditional hit" }
    }

    override fun cacheHit(call: Call, response: Response) {
        traceCall(call) { "cache hit" }
    }

    override fun cacheMiss(call: Call) {
        traceCall(call) { "cache miss" }
    }
}
