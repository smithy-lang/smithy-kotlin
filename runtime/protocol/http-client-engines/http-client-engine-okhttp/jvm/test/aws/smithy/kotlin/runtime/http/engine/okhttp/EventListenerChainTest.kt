/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.okhttp

import okhttp3.*
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventListenerChainTest {
    @Test
    fun testForwardEvents() {
        val eventOrder = mutableListOf<String>()

        val listener1 = TestEventListener("listener1", eventOrder)
        val listener2 = TestEventListener("listener2", eventOrder)

        val chain = EventListenerChain(listOf(listener1, listener2))

        val call = createMockCall()

        // Test forward events
        chain.callStart(call)
        chain.dnsStart(call, "example.com")
        chain.proxySelectStart(call, createHttpUrl())

        // Verify forward events were called in order (listener1 first, then listener2)
        assertEquals("listener1:callStart", eventOrder[0])
        assertEquals("listener2:callStart", eventOrder[1])
        assertEquals("listener1:dnsStart", eventOrder[2])
        assertEquals("listener2:dnsStart", eventOrder[3])
        assertEquals("listener1:proxySelectStart", eventOrder[4])
        assertEquals("listener2:proxySelectStart", eventOrder[5])
    }

    @Test
    fun testReverseEvents() {
        val eventOrder = mutableListOf<String>()

        val listener1 = TestEventListener("listener1", eventOrder)
        val listener2 = TestEventListener("listener2", eventOrder)

        val chain = EventListenerChain(listOf(listener1, listener2))

        val call = createMockCall()

        // Test reverse events
        chain.dnsEnd(call, "example.com", listOf())
        chain.proxySelectEnd(call, createHttpUrl(), listOf())
        chain.callEnd(call)

        // Verify reverse events were called in reverse order (listener2 first, then listener1)
        assertEquals("listener2:dnsEnd", eventOrder[0])
        assertEquals("listener1:dnsEnd", eventOrder[1])
        assertEquals("listener2:proxySelectEnd", eventOrder[2])
        assertEquals("listener1:proxySelectEnd", eventOrder[3])
        assertEquals("listener2:callEnd", eventOrder[4])
        assertEquals("listener1:callEnd", eventOrder[5])
    }

    @Test
    fun testClose() {
        val eventOrder = mutableListOf<String>()

        val listener1 = TestEventListener("listener1", eventOrder)
        val listener2 = TestEventListener("listener2", eventOrder)

        val chain = EventListenerChain(listOf(listener1, listener2))

        // Close the chain
        chain.close()

        // Verify all listeners were closed
        assertTrue(listener1.closed)
        assertTrue(listener2.closed)
    }

    @Test
    fun testMixedEvents() {
        val eventOrder = mutableListOf<String>()

        val listener1 = TestEventListener("listener1", eventOrder)
        val listener2 = TestEventListener("listener2", eventOrder)

        val chain = EventListenerChain(listOf(listener1, listener2))

        val call = createMockCall()

        // Test mixed forward and reverse events
        chain.callStart(call)
        chain.dnsStart(call, "example.com")
        chain.dnsEnd(call, "example.com", listOf())

        // Verify the order of events
        assertEquals("listener1:callStart", eventOrder[0]) // listener1 first (forward)
        assertEquals("listener2:callStart", eventOrder[1]) // listener2 second (forward)
        assertEquals("listener1:dnsStart", eventOrder[2]) // listener1 first (forward)
        assertEquals("listener2:dnsStart", eventOrder[3]) // listener2 second (forward)
        assertEquals("listener2:dnsEnd", eventOrder[4]) // listener2 first (reverse)
        assertEquals("listener1:dnsEnd", eventOrder[5]) // listener1 second (reverse)

        // Clear event order
        eventOrder.clear()

        // Test more events to verify the sequence
        chain.requestHeadersStart(call) // forward event
        chain.requestHeadersEnd(call, Request.Builder().url("https://example.com").build()) // reverse event
        chain.responseHeadersStart(call) // forward event
        chain.responseHeadersEnd(
            call,
            Response.Builder()
                .request(Request.Builder().url("https://example.com").build())
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .build(),
        ) // reverse event

        // Verify the sequence of events
        assertEquals("listener1:requestHeadersStart", eventOrder[0]) // listener1 first (forward)
        assertEquals("listener2:requestHeadersStart", eventOrder[1]) // listener2 second (forward)
        assertEquals("listener2:requestHeadersEnd", eventOrder[2]) // listener2 first (reverse)
        assertEquals("listener1:requestHeadersEnd", eventOrder[3]) // listener1 second (reverse)
        assertEquals("listener1:responseHeadersStart", eventOrder[4]) // listener1 first (forward)
        assertEquals("listener2:responseHeadersStart", eventOrder[5]) // listener2 second (forward)
        assertEquals("listener2:responseHeadersEnd", eventOrder[6]) // listener2 first (reverse)
        assertEquals("listener1:responseHeadersEnd", eventOrder[7]) // listener1 second (reverse)
    }

    // A test EventListener that records the order of calls
    private class TestEventListener(val name: String, val eventOrder: MutableList<String>) :
        EventListener(),
        Closeable {
        var closed = false

        override fun callStart(call: Call) {
            eventOrder.add("$name:callStart")
        }

        override fun dnsStart(call: Call, domainName: String) {
            eventOrder.add("$name:dnsStart")
        }

        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
            eventOrder.add("$name:dnsEnd")
        }

        override fun proxySelectStart(call: Call, url: HttpUrl) {
            eventOrder.add("$name:proxySelectStart")
        }

        override fun proxySelectEnd(call: Call, url: HttpUrl, proxies: List<Proxy>) {
            eventOrder.add("$name:proxySelectEnd")
        }

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            eventOrder.add("$name:connectStart")
        }

        override fun secureConnectStart(call: Call) {
            eventOrder.add("$name:secureConnectStart")
        }

        override fun secureConnectEnd(call: Call, handshake: Handshake?) {
            eventOrder.add("$name:secureConnectEnd")
        }

        override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
            eventOrder.add("$name:connectEnd")
        }

        override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
            eventOrder.add("$name:connectFailed")
        }

        override fun connectionAcquired(call: Call, connection: Connection) {
            eventOrder.add("$name:connectionAcquired")
        }

        override fun connectionReleased(call: Call, connection: Connection) {
            eventOrder.add("$name:connectionReleased")
        }

        override fun requestHeadersStart(call: Call) {
            eventOrder.add("$name:requestHeadersStart")
        }

        override fun requestHeadersEnd(call: Call, request: Request) {
            eventOrder.add("$name:requestHeadersEnd")
        }

        override fun requestBodyStart(call: Call) {
            eventOrder.add("$name:requestBodyStart")
        }

        override fun requestBodyEnd(call: Call, byteCount: Long) {
            eventOrder.add("$name:requestBodyEnd")
        }

        override fun requestFailed(call: Call, ioe: IOException) {
            eventOrder.add("$name:requestFailed")
        }

        override fun responseHeadersStart(call: Call) {
            eventOrder.add("$name:responseHeadersStart")
        }

        override fun responseHeadersEnd(call: Call, response: Response) {
            eventOrder.add("$name:responseHeadersEnd")
        }

        override fun responseBodyStart(call: Call) {
            eventOrder.add("$name:responseBodyStart")
        }

        override fun responseBodyEnd(call: Call, byteCount: Long) {
            eventOrder.add("$name:responseBodyEnd")
        }

        override fun responseFailed(call: Call, ioe: IOException) {
            eventOrder.add("$name:responseFailed")
        }

        override fun callEnd(call: Call) {
            eventOrder.add("$name:callEnd")
        }

        override fun callFailed(call: Call, ioe: IOException) {
            eventOrder.add("$name:callFailed")
        }

        override fun canceled(call: Call) {
            eventOrder.add("$name:canceled")
        }

        override fun satisfactionFailure(call: Call, response: Response) {
            eventOrder.add("$name:satisfactionFailure")
        }

        override fun cacheConditionalHit(call: Call, cachedResponse: Response) {
            eventOrder.add("$name:cacheConditionalHit")
        }

        override fun cacheHit(call: Call, response: Response) {
            eventOrder.add("$name:cacheHit")
        }

        override fun cacheMiss(call: Call) {
            eventOrder.add("$name:cacheMiss")
        }

        override fun close() {
            closed = true
        }
    }

    // Helper methods to create mock objects
    private fun createMockCall(): Call = object : Call {
        override fun cancel() {}
        override fun clone(): Call = this
        override fun enqueue(responseCallback: Callback) {}
        override fun execute(): Response = throw UnsupportedOperationException()
        override fun isCanceled(): Boolean = false
        override fun isExecuted(): Boolean = false
        override fun request(): Request = Request.Builder().url("https://example.com").build()
        override fun timeout(): okio.Timeout = okio.Timeout()
    }

    private fun createHttpUrl(): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host("example.com")
        .build()
}
