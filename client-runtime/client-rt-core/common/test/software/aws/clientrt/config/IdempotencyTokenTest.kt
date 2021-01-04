package software.aws.clientrt.config

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdempotencyTokenTest {

    @Test
    fun defaultIdempotencyTokenProviderImplementationReturnsADifferentTokenWithEachCall() {
        val token1 = IdempotencyTokenProvider.Default.generateToken()
        val token2 = IdempotencyTokenProvider.Default.generateToken()

        assertNotEquals(token1, token2)
    }

    @Test
    fun defaultIdempotencyTokenProviderImplementationReturnsNonEmptyToken() =
        assertTrue(IdempotencyTokenProvider.Default.generateToken().isNotEmpty())
}
