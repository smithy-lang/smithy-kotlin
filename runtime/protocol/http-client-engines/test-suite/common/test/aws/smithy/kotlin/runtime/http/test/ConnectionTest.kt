/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test

// TODO Finish once we have HTTP engine support for client certificates
/*
private const val TLS1_0_URL = "https://localhost:${TestServer.TlsV1.port}/"
private const val TLS1_1_URL = "https://localhost:${TestServer.TlsV1_1.port}/"
private const val TLS1_2_URL = "https://localhost:${TestServer.TlsV1_2.port}/"
private const val TLS1_3_URL = "https://localhost:${TestServer.TlsV1_3.port}/"

class ConnectionTest : AbstractEngineTest() {
    private fun testMinTlsVersion(version: TlsVersion, failUrl: String?, succeedUrl: String) {
        testEngines {
            engineConfig {
                minTlsVersion = version
            }

            failUrl?.let {
                test { env, client ->
                    val req = HttpRequest {
                        testSetup(env)
                        url(Url.parse(failUrl))
                    }

                    val call = client.call(req)
                    call.complete()
                    assertEquals(HttpStatusCode.UpgradeRequired, call.response.status)
                }
            }

            test { env, client ->
                val req = HttpRequest {
                    testSetup(env)
                    url(Url.parse(succeedUrl))
                }

                val call = client.call(req)
                call.complete()
                assertEquals(HttpStatusCode.OK, call.response.status)
            }
        }
    }

    @Test
    fun testMinTls1_0() = testMinTlsVersion(TlsVersion.Tls1_0, null, TLS1_0_URL)

    @Test
    fun testMinTls1_1() = testMinTlsVersion(TlsVersion.Tls1_1, TLS1_0_URL, TLS1_1_URL)

    @Test
    fun testMinTls1_2() = testMinTlsVersion(TlsVersion.Tls1_2, TLS1_1_URL, TLS1_2_URL)

    @Test
    fun testMinTls1_3() = testMinTlsVersion(TlsVersion.Tls1_3, TLS1_2_URL, TLS1_3_URL)
}
*/
