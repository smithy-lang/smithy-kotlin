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
 * Start event are sent in forward order, terminal events are sent in reverse order
 */
internal class EventListenerChain(
    private val listeners: List<EventListener>,
) : EventListener() {
    private val reverseListeners = listeners.reversed()

    fun close() {
        listeners.forEach {
            it.closeIfCloseable()
        }
    }

    override fun callStart(call: Call): Unit =
        listeners.forEach { it.callStart(call) }

    override fun dnsStart(call: Call, domainName: String): Unit =
        listeners.forEach { it.dnsStart(call, domainName) }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>): Unit =
        reverseListeners.forEach { it.dnsEnd(call, domainName, inetAddressList) }

    override fun proxySelectStart(call: Call, url: HttpUrl): Unit =
        listeners.forEach { it.proxySelectStart(call, url) }

    override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>): Unit =
        reverseListeners.forEach { it.proxySelectEnd(call, url, proxies) }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy): Unit =
        listeners.forEach { it.connectStart(call, inetSocketAddress, proxy) }

    override fun secureConnectStart(call: Call): Unit =
        listeners.forEach { it.secureConnectStart(call) }

    override fun secureConnectEnd(call: Call, handshake: Handshake?): Unit =
        reverseListeners.forEach { it.secureConnectEnd(call, handshake) }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?): Unit =
        reverseListeners.forEach { it.connectEnd(call, inetSocketAddress, proxy, protocol) }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException): Unit =
        reverseListeners.forEach { it.connectFailed(call, inetSocketAddress, proxy, protocol, ioe) }

    override fun connectionAcquired(call: Call, connection: Connection): Unit =
        listeners.forEach { it.connectionAcquired(call, connection) }

    override fun connectionReleased(call: Call, connection: Connection): Unit =
        reverseListeners.forEach { it.connectionReleased(call, connection) }

    override fun requestHeadersStart(call: Call): Unit =
        listeners.forEach { it.requestHeadersStart(call) }

    override fun requestHeadersEnd(call: Call, request: Request): Unit =
        reverseListeners.forEach { it.requestHeadersEnd(call, request) }

    override fun requestBodyStart(call: Call): Unit =
        listeners.forEach { it.requestBodyStart(call) }

    override fun requestBodyEnd(call: Call, byteCount: Long): Unit =
        reverseListeners.forEach { it.requestBodyEnd(call, byteCount) }

    override fun requestFailed(call: Call, ioe: IOException): Unit =
        reverseListeners.forEach { it.requestFailed(call, ioe) }

    override fun responseHeadersStart(call: Call): Unit =
        listeners.forEach { it.responseHeadersStart(call) }

    override fun responseHeadersEnd(call: Call, response: Response): Unit =
        reverseListeners.forEach { it.responseHeadersEnd(call, response) }

    override fun responseBodyStart(call: Call): Unit =
        listeners.forEach { it.responseBodyStart(call) }

    override fun responseBodyEnd(call: Call, byteCount: Long): Unit =
        reverseListeners.forEach { it.responseBodyEnd(call, byteCount) }

    override fun responseFailed(call: Call, ioe: IOException): Unit =
        reverseListeners.forEach { it.responseFailed(call, ioe) }

    override fun callEnd(call: Call): Unit =
        reverseListeners.forEach { it.callEnd(call) }

    override fun callFailed(call: Call, ioe: IOException): Unit =
        reverseListeners.forEach { it.callFailed(call, ioe) }

    override fun canceled(call: Call): Unit =
        reverseListeners.forEach { it.canceled(call) }

    override fun satisfactionFailure(call: Call, response: Response): Unit =
        reverseListeners.forEach { it.satisfactionFailure(call, response) }

    override fun cacheConditionalHit(call: Call, cachedResponse: Response): Unit =
        listeners.forEach { it.cacheConditionalHit(call, cachedResponse) }

    override fun cacheHit(call: Call, response: Response): Unit =
        listeners.forEach { it.cacheHit(call, response) }

    override fun cacheMiss(call: Call): Unit =
        listeners.forEach { it.cacheMiss(call) }
}
