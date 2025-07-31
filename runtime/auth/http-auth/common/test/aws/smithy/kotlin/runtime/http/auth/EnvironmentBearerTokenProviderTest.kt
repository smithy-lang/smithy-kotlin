package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.util.TestPlatformProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvironmentBearerTokenProviderTest {
    @Test
    fun testResolveFromEnvVar() = runTest {
        val provider = EnvironmentBearerTokenProvider(
            "TEST_SYS_PROPS_TOKEN",
            "TEST_ENV_TOKEN",
            TestPlatformProvider(
                env = mutableMapOf("TEST_ENV_TOKEN" to "test-env-bearer-token"),
            ),
        )

        val token = provider.resolve()

        assertEquals("test-env-bearer-token", token.token)
    }

    @Test
    fun testResolveFromSysProps() = runTest {
        val provider = EnvironmentBearerTokenProvider(
            "TEST_SYS_PROPS_TOKEN",
            "TEST_ENV_TOKEN",
            TestPlatformProvider(
                props = mutableMapOf("TEST_SYS_PROPS_TOKEN" to "test-sys-props-bearer-token"),
            ),
        )

        val token = provider.resolve()

        assertEquals("test-sys-props-bearer-token", token.token)
    }

    @Test
    fun testResolutionOrder() = runTest {
        val provider = EnvironmentBearerTokenProvider(
            "TEST_SYS_PROPS_TOKEN",
            "TEST_ENV_TOKEN",
            TestPlatformProvider(
                props = mutableMapOf("TEST_SYS_PROPS_TOKEN" to "test-sys-props-bearer-token"),
                env = mutableMapOf("TEST_ENV_TOKEN" to "test-env-bearer-token"),
            ),
        )

        val token = provider.resolve()

        assertEquals("test-sys-props-bearer-token", token.token)
    }

    @Test
    fun testResolveWithMissingToken() = runTest {
        val provider = EnvironmentBearerTokenProvider(
            "MISSING_TEST_TOKEN",
            "MISSING_TEST_TOKEN",
            TestPlatformProvider(
                env = mutableMapOf("TEST_TOKEN" to "test-env-bearer-token"),
                props = mutableMapOf("TEST_TOKEN" to "test-sys-props-bearer-token"),
            ),
        )

        val exception = assertFailsWith<IllegalStateException> {
            provider.resolve()
        }
        assertEquals("neither system property MISSING_TEST_TOKEN nor environment variable MISSING_TEST_TOKEN is set", exception.message)
    }
}
