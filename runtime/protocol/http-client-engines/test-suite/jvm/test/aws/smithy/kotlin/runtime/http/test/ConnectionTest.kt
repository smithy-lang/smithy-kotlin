/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.content.decodeToString
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.TlsContext
import aws.smithy.kotlin.runtime.http.engine.crt.CrtHttpEngineConfig
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngineConfig
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.test.util.*
import aws.smithy.kotlin.runtime.net.TlsVersion
import aws.smithy.kotlin.runtime.net.toUrlString
import kotlinx.coroutines.delay
import javax.net.ssl.HostnameVerifier
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConnectionTest : AbstractEngineTest() {
    private fun testTlsConfigs(
        testName: String,
        serverType: ServerType,
        tlsContext: TlsContext = TlsContext {},
        okHttpConfigBlock: OkHttpEngineConfig.Builder.() -> Unit = {},
        crtConfigBlock: CrtHttpEngineConfig.Builder.() -> Unit = {},
    ) {
        val url = testServers.getValue(serverType)

        testSslConfig.useAsSystemProperties {
            testEngines(skipEngines = setOf("CrtHttpEngine")) {
                engineConfig {
                    this.tlsContext = tlsContext

                    if (this is OkHttpEngineConfig.Builder) {
                        okHttpConfigBlock()
                    }

                    if (this is CrtHttpEngineConfig.Builder) {
                        crtConfigBlock()
                    }
                }

                test { _, client ->
                    val req = HttpRequest {
                        testSetup(url)
                        method = HttpMethod.POST
                        url {
                            path.decoded = "/tlsVerification"
                        }
                        body = HttpBody.fromBytes(testName.encodeToByteArray())
                    }

                    val call = client.call(req)
                    assertEquals(HttpStatusCode.OK, call.response.status)
                    val body = call.response.body.toByteStream()?.decodeToString()
                    assertEquals("Received body: $testName", body)
                    call.complete()
                }
            }
        }
    }

    // FIXME Setting min TLS version on the engine is insufficient. Something deep in the configuration of the client
    //  and server need to agree.
    /*
    @Test
    fun testMinTls1_0() = testTlsConfigs("testMinTls1_0", TlsVersion.TLS_1_0, ServerType.TLS_1_0)

    @Test
    fun testMinTls1_1() = testTlsConfigs("testMinTls1_1", TlsVersion.TLS_1_1, ServerType.TLS_1_1)
     */

    @Test
    fun testMinTls1_2_vs_Tls_1_1() {
        val e = assertFailsWith<HttpException> {
            testTlsConfigs("testMinTls1_2", ServerType.TLS_1_1, TlsContext { minVersion = TlsVersion.TLS_1_2 })
        }
        assertEquals(HttpErrorCode.TLS_NEGOTIATION_ERROR, e.errorCode)
    }
    @Test
    fun testMinTls1_2() = testTlsConfigs("testMinTls1_2", ServerType.TLS_1_2, TlsContext { minVersion = TlsVersion.TLS_1_2 })

    @Test
    fun testMinTls1_3_vs_Tls_1_2() {
        val e = assertFailsWith<HttpException> {
            testTlsConfigs("testMinTls1_3_vs_Tls_1_2", ServerType.TLS_1_2, TlsContext { minVersion = TlsVersion.TLS_1_3 })
        }
        assertEquals(HttpErrorCode.TLS_NEGOTIATION_ERROR, e.errorCode)
    }

    @Test
    fun testMinTls1_3() = testTlsConfigs("testMinTls1_3", ServerType.TLS_1_3, TlsContext { minVersion = TlsVersion.TLS_1_3 })

    @Test
    fun testTrustManagerWithTls1_2() {
        testTlsConfigs(
            "testTrustManagerWithTls1_2",
            ServerType.TLS_1_2,
            TlsContext { minVersion = TlsVersion.TLS_1_2 },
            okHttpConfigBlock = {
                trustManagerProvider = createTestTrustManagerProvider(testCert)
            },
        )
    }

    @Test
    fun testTrustManagerWithTls1_3() {
        testTlsConfigs(
            "testTrustManagerWithTls1_3",
            ServerType.TLS_1_3,
            TlsContext { minVersion = TlsVersion.TLS_1_3 },
            okHttpConfigBlock = {
                trustManagerProvider = createTestTrustManagerProvider(testCert)
            },
        )
    }

    // TODO: Add mutual TLS (mTLS) tests once mTls implementation is available
    // This would test keyManagerProvider with servers that require client certificates

    @Test
    fun testCipherSuitesWithTls1_2() {
        testTlsConfigs(
            "testCipherSuitesWithTls1_2",
            ServerType.TLS_1_2,
            TlsContext { minVersion = TlsVersion.TLS_1_2 },
            okHttpConfigBlock = {
                cipherSuites = listOf("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
            },
        )
    }

    @Test
    fun testCipherSuitesWithTls1_3() {
        // test cipher suites not compatible with Tls1_3
        val e = assertFailsWith<HttpException> {
            testTlsConfigs(
                "testCipherSuitesWithTls1_3",
                ServerType.TLS_1_3,
                TlsContext { minVersion = TlsVersion.TLS_1_3 },
                okHttpConfigBlock = {
                    cipherSuites = listOf("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
                },
            )
        }
        assertEquals(HttpErrorCode.TLS_NEGOTIATION_ERROR, e.errorCode)

        // test cipher suites compatible with Tls1_3
        testTlsConfigs(
            "testCipherSuitesWithTls1_3",
            ServerType.TLS_1_3,
            TlsContext { minVersion = TlsVersion.TLS_1_3 },
            okHttpConfigBlock = {
                cipherSuites = listOf("TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256")
            },
        )
    }

    @Test
    fun testHostnameVerifier() {
        testTlsConfigs(
            "testHostnameVerifier",
            ServerType.TLS_1_2,
            okHttpConfigBlock = {
                hostnameVerifier = HostnameVerifier { hostname, _ ->
                    hostname == testServers.getValue(ServerType.TLS_1_2).host.toUrlString()
                }
            },
        )
    }

    @Test
    fun testCertificatePinner() {
        testTlsConfigs(
            "testCertificatePinner",
            ServerType.TLS_1_2,
            okHttpConfigBlock = {
                certificatePinner = createTestCertificatePinner(testCert, ServerType.TLS_1_2)
            },
        )
    }

    @Test
    fun testCaRoot() {
        testTlsConfigs(
            "testCaRoot",
            ServerType.TLS_1_2,
            crtConfigBlock = {
                caRoot = createTestPemCert(testCert)
            },
        )
    }

    @Test
    fun testCaFile() {
        val tempFile = createTempFile("ca-cert", ".pem").toFile()
        try {
            tempFile.writeText(createTestPemCert(testCert))
            testTlsConfigs(
                "testCaFile",
                ServerType.TLS_1_2,
                crtConfigBlock = {
                    caFile = tempFile.absolutePath
                },
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testCaDir() {
        val tempDir = createTempDirectory("ca-certs").toFile()
        try {
            val certFile = tempDir.resolve("ca-cert.pem")
            certFile.writeText(createTestPemCert(testCert))

            testTlsConfigs(
                "testCaDir",
                ServerType.TLS_1_2,
                crtConfigBlock = {
                    caDir = tempDir.absolutePath
                },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testVerifyPeerFalse() {
        testTlsConfigs(
            "testVerifyPeers",
            ServerType.TLS_1_2,
            crtConfigBlock = {
                caRoot = createInvalidTestPemCert()
                verifyPeer = false
            },
        )
    }

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
