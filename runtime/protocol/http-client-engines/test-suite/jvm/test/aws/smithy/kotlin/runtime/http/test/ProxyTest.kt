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
import kotlin.test.assertEquals

private const val PROXY_SERVER: String = "http://127.0.0.1:8020"

class ProxyTest : AbstractEngineTest() {

    @Test
    fun testHttpProxy() = testEngines {
        engineConfig {
            proxyConfig = ProxyConfig.Http(PROXY_SERVER)
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
