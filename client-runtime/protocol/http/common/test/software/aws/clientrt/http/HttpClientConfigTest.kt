/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import software.aws.clientrt.http.engine.HttpClientEngine
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.response.HttpResponse

class TestFeature(val name: String) : Feature {

    class Config {
        var name: String? = null
    }

    override fun install(client: SdkHttpClient) {
        throw RuntimeException("test install")
    }

    companion object Feature : HttpClientFeatureFactory<Config, TestFeature> {

        override val key: FeatureKey<TestFeature> = FeatureKey("TestFeature")

        override fun create(block: Config.() -> Unit): TestFeature {
            val config = Config().apply(block)
            if (config.name == null) throw RuntimeException("configuration error")
            return TestFeature(config.name!!)
        }
    }
}

class HttpClientConfigTest {
    @Test
    fun `it configures features on install`() {
        val config = HttpClientConfig()
        var called = false
        config.install(TestFeature) {
            name = "Testing"
            called = true
        }
        assertTrue(called)
    }

    @Test
    fun `it allows features to install on client`() {
        val mockEngine = object : HttpClientEngine {
            override suspend fun roundTrip(requestBuilder: HttpRequestBuilder): HttpResponse {
                TODO("test engine")
            }
        }

        assertFailsWith<RuntimeException>("test install") {
            sdkHttpClient(mockEngine) {
                install(TestFeature) {
                    name = "Testing"
                }
            }
        }
    }
}
