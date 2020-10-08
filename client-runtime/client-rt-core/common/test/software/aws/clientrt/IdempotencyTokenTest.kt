package software.aws.clientrt

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdempotencyTokenTest {

    @Test
    fun `default idempotencyTokenProvider implementation returns a different token with each call`() {
        val token1 = defaultIdempotencyTokenProvider.invoke()
        val token2 = defaultIdempotencyTokenProvider.invoke()

        assertNotEquals(token1, token2)
    }

    @Test
    fun `default idempotencyTokenProvider implementation returns non empty token`() =
        assertTrue(defaultIdempotencyTokenProvider.invoke().isNotEmpty())
}
