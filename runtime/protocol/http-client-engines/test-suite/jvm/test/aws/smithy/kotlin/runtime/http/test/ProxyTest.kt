/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.complete
import aws.smithy.kotlin.runtime.http.engine.ProxyConfig
import aws.smithy.kotlin.runtime.http.engine.ProxySelector
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.engineConfig
import aws.smithy.kotlin.runtime.http.test.util.test
import aws.smithy.kotlin.runtime.net.url.Url
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // enables non-static @BeforeAll/@AfterAll methods
@EnabledIfSystemProperty(named = "aws.test.http.enableProxyTests", matches = "true")
class ProxyTest : AbstractEngineTest() {
    private lateinit var mitmProxy: MitmContainer

    @BeforeAll
    fun setUp() {
        mitmProxy = MitmContainer("--set", "fakeupstream=aws.amazon.com")
    }

    @AfterAll
    fun cleanUp() {
        mitmProxy.close()
    }

    @Test
    fun testHttpProxy() = testEngines {
        engineConfig {
            val hostPort = mitmProxy.hostPort
            proxySelector = ProxySelector {
                ProxyConfig.Http("http://127.0.0.1:$hostPort")
            }
        }

        test { _, client ->
            testProxyResponse(client)
        }
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // enables non-static @BeforeAll/@AfterAll methods
@EnabledIfSystemProperty(named = "aws.test.http.enableProxyTests", matches = "true")
class ProxyAuthTest : AbstractEngineTest() {
    private lateinit var mitmProxy: MitmContainer

    @BeforeAll
    fun setUp() {
        mitmProxy = MitmContainer("--proxyauth", "testuser:testpass", "--set", "fakeupstream=aws.amazon.com")
    }

    @AfterAll
    fun cleanUp() {
        mitmProxy.close()
    }

    @Test
    fun testHttpProxyAuth() = testEngines {
        engineConfig {
            val hostPort = mitmProxy.hostPort
            proxySelector = ProxySelector {
                ProxyConfig.Http("http://testuser:testpass@127.0.0.1:$hostPort")
            }
        }

        test { _, client ->
            testProxyResponse(client)
        }
    }
}

private suspend fun testProxyResponse(client: SdkHttpClient) {
    val req = HttpRequest {
        // Use http for two reasons (1) mitmproxy wants to sniff traffic by default
        // and (2) we know the response code will be a redirect if our setup fails.
        // NOTE: you can still use mitmproxy to proxy https traffic without intercepting it
        // like any normal proxy by setting the `--ignore-hosts` option to forward all traffic
        // but that would mean we make an actual request to the origin
        url(Url.parse("http://aws.amazon.com/"))
        header("Host", "aws.amazon.com")
    }

    val call = client.call(req)
    try {
        // will be a 301 for http -> https if proxy isn't setup or not configured properly
        assertEquals(HttpStatusCode.OK, call.response.status, "${client.engine}")
        val body = call.response.body.readAll()!!.decodeToString()
        assertEquals("hello proxy", body)
    } finally {
        call.complete()
    }
}
