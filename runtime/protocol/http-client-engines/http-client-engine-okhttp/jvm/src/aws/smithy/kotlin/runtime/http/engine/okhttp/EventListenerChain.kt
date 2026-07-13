/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.io.closeIfCloseable
import okhttp3.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * An [okhttp3.EventListener] that delegates to a chain of EventListeners.
 * Start events are sent in forward order, terminal events are sent in reverse order.
 * Uses arrays for iteration to avoid List iterator allocation on each event dispatch.
 */
internal class EventListenerChain(
    listeners: List<EventListener>,
) : EventListener() {
    private val forward: Array<EventListener> = listeners.toTypedArray()
    private val reverse: Array<EventListener> = listeners.asReversed().toTypedArray()

    fun close() {
        for (listener in forward) {
            listener.closeIfCloseable()
        }
    }

    override fun callStart(call: Call) {
        for (l in forward) l.callStart(call)
    }

    override fun dnsStart(call: Call, domainName: String) {
        for (l in forward) l.dnsStart(call, domainName)
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        for (l in reverse) l.dnsEnd(call, domainName, inetAddressList)
    }

    override fun proxySelectStart(call: Call, url: HttpUrl) {
        for (l in forward) l.proxySelectStart(call, url)
    }

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) {
        for (l in reverse) l.proxySelectEnd(call, url, proxies)
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        for (l in forward) l.connectStart(call, inetSocketAddress, proxy)
    }

    override fun secureConnectStart(call: Call) {
        for (l in forward) l.secureConnectStart(call)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        for (l in reverse) l.secureConnectEnd(call, handshake)
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        for (l in reverse) l.connectEnd(call, inetSocketAddress, proxy, protocol)
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        for (l in reverse) l.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        for (l in forward) l.connectionAcquired(call, connection)
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        for (l in reverse) l.connectionReleased(call, connection)
    }

    override fun requestHeadersStart(call: Call) {
        for (l in forward) l.requestHeadersStart(call)
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        for (l in reverse) l.requestHeadersEnd(call, request)
    }

    override fun requestBodyStart(call: Call) {
        for (l in forward) l.requestBodyStart(call)
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        for (l in reverse) l.requestBodyEnd(call, byteCount)
    }

    override fun requestFailed(call: Call, ioe: IOException) {
        for (l in reverse) l.requestFailed(call, ioe)
    }

    override fun responseHeadersStart(call: Call) {
        for (l in forward) l.responseHeadersStart(call)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        for (l in reverse) l.responseHeadersEnd(call, response)
    }

    override fun responseBodyStart(call: Call) {
        for (l in forward) l.responseBodyStart(call)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        for (l in reverse) l.responseBodyEnd(call, byteCount)
    }

    override fun responseFailed(call: Call, ioe: IOException) {
        for (l in reverse) l.responseFailed(call, ioe)
    }

    override fun callEnd(call: Call) {
        for (l in reverse) l.callEnd(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        for (l in reverse) l.callFailed(call, ioe)
    }

    override fun canceled(call: Call) {
        for (l in reverse) l.canceled(call)
    }

    override fun satisfactionFailure(call: Call, response: Response) {
        for (l in reverse) l.satisfactionFailure(call, response)
    }

    override fun cacheConditionalHit(call: Call, cachedResponse: Response) {
        for (l in forward) l.cacheConditionalHit(call, cachedResponse)
    }

    override fun cacheHit(call: Call, response: Response) {
        for (l in forward) l.cacheHit(call, response)
    }

    override fun cacheMiss(call: Call) {
        for (l in forward) l.cacheMiss(call)
    }
}
