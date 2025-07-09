package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.util.TestPlatformProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvironmentBearerTokenProviderTest {
    class MutableTestPlatformProvider(
        private val mutableEnv: MutableMap<String, String> = mutableMapOf(),
    ) : TestPlatformProvider(env = mutableEnv) {

        fun setEnv(key: String, value: String) {
            mutableEnv[key] = value
        }

        override fun getenv(key: String): String? = mutableEnv[key]
    }

    @Test
    fun testResolveWithValidToken() = runTest {
        val provider = EnvironmentBearerTokenProvider(
            "TEST_TOKEN",
            MutableTestPlatformProvider(mutableMapOf("TEST_TOKEN" to "test-bearer-token")),
        )

        val token = provider.resolve()

        assertEquals("test-bearer-token", token.token)
    }

    @Test
    fun testResolveWithMissingToken() = runTest {
        val provider = EnvironmentBearerTokenProvider(
            "MISSING_TEST_TOKEN",
            MutableTestPlatformProvider(mutableMapOf()),
        )

        val exception = assertFailsWith<IllegalStateException> {
            provider.resolve(emptyAttributes())
        }
        assertEquals("MISSING_TEST_TOKEN environment variable is not set", exception.message)
    }

    @Test
    fun testResolveChecksEnvironmentOnEachCall() = runTest {
        val envVars = mutableMapOf("DYNAMIC_TEST_TOKEN" to "initial-bearer-token")
        val testPlatformProvider = MutableTestPlatformProvider(envVars)
        val provider = EnvironmentBearerTokenProvider(
            "DYNAMIC_TEST_TOKEN",
            testPlatformProvider,
        )

        assertEquals("initial-bearer-token", provider.resolve().token)
        testPlatformProvider.setEnv("DYNAMIC_TEST_TOKEN", "updated-bearer-token")
        assertEquals("updated-bearer-token", provider.resolve().token)
    }
}
