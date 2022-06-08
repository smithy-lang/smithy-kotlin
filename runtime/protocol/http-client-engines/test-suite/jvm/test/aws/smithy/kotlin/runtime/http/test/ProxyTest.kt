/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.Url
import aws.smithy.kotlin.runtime.http.engine.ProxyConfig
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.response.complete
import aws.smithy.kotlin.runtime.http.test.util.AbstractEngineTest
import aws.smithy.kotlin.runtime.http.test.util.engineConfig
import aws.smithy.kotlin.runtime.http.test.util.test
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals

// defined by gradle script
private val PROXY_SCRIPT_ROOT = System.getProperty("MITM_PROXY_SCRIPTS_ROOT")
private fun mitmProxyContainer(
    vararg options: String
) = GenericContainer(DockerImageName.parse("mitmproxy/mitmproxy:8.1.0"))
    .withExposedPorts(8080)
    .withFileSystemBind(PROXY_SCRIPT_ROOT, "/home/mitmproxy/scripts", BindMode.READ_ONLY)
    .withLogConsumer {
        print(it.utf8String)
    }.apply {
        val command = buildString {
            append("mitmdump --flow-detail 2 -s /home/mitmproxy/scripts/fakeupstream.py")
            append(options.joinToString(separator = " ", prefix = " "))
        }
        withCommand(command)
    }

@Testcontainers
@DisabledIfSystemProperty(named = "aws.test.http.enableProxyTests", matches = "false")
class ProxyTest : AbstractEngineTest() {

    @Container
    val mitmProxy = mitmProxyContainer("--set fakeupstream=aws.amazon.com")

    @Test
    fun testHttpProxy() = testEngines {
        engineConfig {
            val proxyPort = mitmProxy.getMappedPort(8080)
            proxyConfig = ProxyConfig.Http("http://127.0.0.1:$proxyPort")
        }

        test { _, client ->
            val req = HttpRequest {
                url(Url.parse("http://aws.amazon.com"))
                url.path = "/"
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
    }
}

@Testcontainers
@DisabledIfSystemProperty(named = "aws.test.http.enableProxyTests", matches = "false")
class ProxyAuthTest : AbstractEngineTest() {

    @Container
    val mitmProxy = mitmProxyContainer("--proxyauth testuser:testpass --set fakeupstream=aws.amazon.com")

    @Test
    fun testHttpProxyAuth() = testEngines {
        engineConfig {
            val proxyPort = mitmProxy.getMappedPort(8080)
            proxyConfig = ProxyConfig.Http("http://testuser:testpass@127.0.0.1:$proxyPort")
        }

        test { _, client ->
            val req = HttpRequest {
                url(Url.parse("http://aws.amazon.com"))
                url.path = "/"
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
    }
}
