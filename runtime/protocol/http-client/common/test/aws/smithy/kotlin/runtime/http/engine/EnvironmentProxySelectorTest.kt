/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.net.Url
import aws.smithy.kotlin.runtime.util.PlatformEnvironProvider
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

data class TestPlatformEnvironmentProvider(
    private val env: Map<String, String> = emptyMap(),
    private val props: Map<String, String> = emptyMap(),
) : PlatformEnvironProvider {
    override fun getenv(key: String): String? = env[key]
    override fun getAllEnvVars(): Map<String, String> = env
    override fun getProperty(key: String): String? = props[key]
    override fun getAllProperties(): Map<String, String> = props
}

class EnvironmentProxySelectorTest {
    private data class TestCase(
        val expected: ProxyConfig,
        val url: String = "https://aws.amazon.com",
        val env: Map<String, String> = emptyMap(),
        val props: Map<String, String> = emptyMap(),
    )

    private val httpsProxyEnv = mapOf("https_proxy" to "http://test.proxy.aws")
    private val httpProxyEnv = mapOf("http_proxy" to "http://test.proxy.aws")

    private val httpsProxyProps = mapOf(
        "https.proxyHost" to "test.proxy.aws",
        // defaults to the protocol (443), set it to 80 to re-use the expected proxy config between
        // environment and property tests
        "https.proxyPort" to "80",
    )
    private val httpProxyProps = mapOf("http.proxyHost" to "test.proxy.aws")

    private val expectedProxyConfig = ProxyConfig.Http(Url.parse("http://test.proxy.aws"))

    private val tests = listOf(
        // no props
        TestCase(ProxyConfig.Direct),

        // no proxy
        TestCase(ProxyConfig.Direct, env = mapOf("no_proxy" to "aws.amazon.com") + httpsProxyEnv),
        TestCase(ProxyConfig.Direct, env = mapOf("no_proxy" to ".amazon.com") + httpsProxyEnv),
        TestCase(ProxyConfig.Direct, props = mapOf("http.noProxyHosts" to "aws.amazon.com") + httpsProxyProps),
        TestCase(ProxyConfig.Direct, props = mapOf("http.noProxyHosts" to ".amazon.com") + httpsProxyProps),

        // multiple no proxy hosts normalization
        TestCase(ProxyConfig.Direct, env = mapOf("no_proxy" to "example.com,.amazon.com") + httpsProxyEnv),
        TestCase(ProxyConfig.Direct, props = mapOf("http.noProxyHosts" to "example.com|.amazon.com") + httpsProxyProps),

        // environment
        TestCase(expectedProxyConfig, env = httpsProxyEnv),
        TestCase(expectedProxyConfig, props = httpsProxyProps),

        // unmatched scheme (https url with http proxy set)
        TestCase(ProxyConfig.Direct, env = httpProxyEnv),
        TestCase(ProxyConfig.Direct, props = httpProxyProps),

        // no_proxy set but doesn't match
        TestCase(expectedProxyConfig, env = httpsProxyEnv + mapOf("no_proxy" to "example.com")),
    )

    @Test
    fun testSelect() {
        tests.forEachIndexed { idx, testCase ->
            val testProvider = TestPlatformEnvironmentProvider(testCase.env, testCase.props)
            val selector = EnvironmentProxySelector(testProvider)
            val url = Url.parse(testCase.url)
            val actual = selector.select(url)
            assertEquals(testCase.expected, actual, "[idx=$idx] expected $testCase resulted in proxy config: $actual")
        }
    }

    private data class FailCase(
        val env: Map<String, String> = emptyMap(),
        val props: Map<String, String> = emptyMap(),
    )

    private val failCases = listOf(
        // Invalid ports specified
        FailCase(props = mapOf("http.proxyHost" to "test.proxy.aws", "http.proxyPort" to "0")),
        FailCase(props = mapOf("http.proxyHost" to "test.proxy.aws", "http.proxyPort" to "x")),
        FailCase(props = mapOf("https.proxyHost" to "test.proxy.aws", "https.proxyPort" to "0")),
        FailCase(props = mapOf("https.proxyHost" to "test.proxy.aws", "https.proxyPort" to "x")),
        FailCase(env = mapOf("http_proxy" to "http://test.proxy.aws:0")),
        FailCase(env = mapOf("http_proxy" to "http://test.proxy.aws:x")),
        FailCase(env = mapOf("https_proxy" to "https://test.proxy.aws:0")),
        FailCase(env = mapOf("https_proxy" to "https://test.proxy.aws:x")),
    )

    @Test
    fun testSelectFailures() {
        failCases.forEachIndexed { idx, failCase ->
            val testProvider = TestPlatformEnvironmentProvider(failCase.env, failCase.props)
            val exception = assertThrows<ClientException>("[idx=$idx] expected ClientException") {
                EnvironmentProxySelector(testProvider)
            }

            val expectedError = (failCase.env + failCase.props).map { (k, v) -> """$k="$v"""" }.joinToString(", ")

            assertContains(
                exception.message!!,
                expectedError,
                message = "[idx=$idx] unexpected error message ${exception.message}",
            )
        }
    }
}
