package software.aws.clientrt.config

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdempotencyTokenTest {

    @Test
    fun `default idempotencyTokenProvider implementation returns a different token with each call`() {
        val token1 = IdempotencyTokenProvider.Default.generateToken()
        val token2 = IdempotencyTokenProvider.Default.generateToken()

        assertNotEquals(token1, token2)
    }

    @Test
    fun `default idempotencyTokenProvider implementation returns non empty token`() =
        assertTrue(IdempotencyTokenProvider.Default.generateToken().isNotEmpty())
}
