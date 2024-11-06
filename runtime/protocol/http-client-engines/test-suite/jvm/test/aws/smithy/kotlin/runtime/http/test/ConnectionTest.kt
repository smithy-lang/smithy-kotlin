/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.content.decodeToString
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngineConfig
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.test.util.*
import aws.smithy.kotlin.runtime.http.test.util.testServers
import aws.smithy.kotlin.runtime.net.TlsVersion
import kotlinx.coroutines.delay
import java.nio.file.Paths
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConnectionTest : AbstractEngineTest() {
    private fun testMinTlsVersion(version: TlsVersion, serverType: ServerType) {
        val url = testServers.getValue(serverType)

        val sslConfigPath = System.getProperty("SSL_CONFIG_PATH")
        val sslConfig = SslConfig.load(Paths.get(sslConfigPath))

        // Set SSL certs via system properties which HTTP clients should pick up
        sslConfig.useAsSystemProperties {
            testEngines(skipEngines = setOf("CrtHttpEngine")) {
                engineConfig {
                    tlsContext {
                        minVersion = version
                    }
                }

                test { _, client ->
                    val bodyText = "Testing $version"

                    val req = HttpRequest {
                        testSetup(url)
                        method = HttpMethod.POST
                        url {
                            path.decoded = "/tlsVerification"
                        }
                        body = HttpBody.fromBytes(bodyText.encodeToByteArray())
                    }

                    val call = client.call(req)
                    assertEquals(HttpStatusCode.OK, call.response.status)
                    val body = call.response.body.toByteStream()?.decodeToString()
                    assertEquals("Received body: $bodyText", body)
                    call.complete()
                }
            }
        }
    }

    // FIXME Setting min TLS version on the engine is insufficient. Something deep in the configuration of the client
    //  and server need to agree.
    /*
    @Test
    fun testMinTls1_0() = testMinTlsVersion(TlsVersion.TLS_1_0, ServerType.TLS_1_0)

    @Test
    fun testMinTls1_1() = testMinTlsVersion(TlsVersion.TLS_1_1, ServerType.TLS_1_1)
     */

    @Test
    fun testMinTls1_2_vs_Tls_1_1() {
        val e = assertFailsWith<HttpException> { testMinTlsVersion(TlsVersion.TLS_1_2, ServerType.TLS_1_1) }
        assertEquals(HttpErrorCode.TLS_NEGOTIATION_ERROR, e.errorCode)
    }

    @Test
    fun testMinTls1_2() = testMinTlsVersion(TlsVersion.TLS_1_2, ServerType.TLS_1_2)

    @Test
    fun testMinTls1_3_vs_Tls_1_2() {
        val e = assertFailsWith<HttpException> { testMinTlsVersion(TlsVersion.TLS_1_3, ServerType.TLS_1_2) }
        assertEquals(HttpErrorCode.TLS_NEGOTIATION_ERROR, e.errorCode)
    }

    @Test
    fun testMinTls1_3() = testMinTlsVersion(TlsVersion.TLS_1_3, ServerType.TLS_1_3)

    // See https://github.com/awslabs/aws-sdk-kotlin/issues/1214
    @Test
    fun testShortLivedConnections() = testEngines(
        // Only run this test on OkHttp
        skipEngines = setOf("CrtHttpEngine", "OkHttp4Engine"),
    ) {
        engineConfig {
            this as OkHttpEngineConfig.Builder
            connectionIdlePollingInterval = 200.milliseconds
            connectionIdleTimeout = 10.seconds // Longer than the server-side timeout
        }

        test { _, client ->
            val initialReq = HttpRequest {
                testSetup()
                method = HttpMethod.POST
                url {
                    path.decoded = "/connectionDrop"
                }
                body = "Foo".toHttpBody()
            }
            val initialCall = client.call(initialReq)
            val initialResp = initialCall.response.body.toByteStream()?.decodeToString()
            assertEquals("Bar", initialResp)

            delay(5.seconds) // Longer than the service side timeout, shorter than the client-side timeout

            val subsequentReq = HttpRequest {
                testSetup()
                method = HttpMethod.POST
                url {
                    path.decoded = "/connectionDrop"
                }
                body = "Foo".toHttpBody()
            }
            val subsequentCall = client.call(subsequentReq)
            val subsequentResp = subsequentCall.response.body.toByteStream()?.decodeToString()
            assertEquals("Bar", subsequentResp)
        }
    }
}
