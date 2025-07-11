package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.util.TestPlatformProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvironmentBearerTokenProviderTest {
    @Test
    fun testResolveWithValidToken() = runTest {
        val provider = EnvironmentBearerTokenProvider(
            "TEST_TOKEN",
            TestPlatformProvider(mutableMapOf("TEST_TOKEN" to "test-bearer-token")),
        )

        val token = provider.resolve()

        assertEquals("test-bearer-token", token.token)
    }

    @Test
    fun testResolveWithMissingToken() = runTest {
        val provider = EnvironmentBearerTokenProvider(
            "MISSING_TEST_TOKEN",
            TestPlatformProvider(mutableMapOf()),
        )

        val exception = assertFailsWith<IllegalStateException> {
            provider.resolve(emptyAttributes())
        }
        assertEquals("MISSING_TEST_TOKEN environment variable is not set", exception.message)
    }
}
