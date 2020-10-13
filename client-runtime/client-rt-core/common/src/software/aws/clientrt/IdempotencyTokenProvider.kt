package software.aws.clientrt

import software.aws.clientrt.time.Instant
import kotlin.random.Random

/**
 * Describes a function and default implementation to produce a string used as a token to dedupe
 * requests from the client.
 */
fun interface IdempotencyTokenProvider {

    /**
     * Generate a unique, UUID-like string that can be used to track unique client-side requests.
     */
    fun generateToken(): String

    /**
     * This is the default function to generate a UUID for idempotency tokens if they are not specified
     * in client code.
     *
     * TODO: Implement a real function.  See https://www.pivotaltracker.com/story/show/174214013
     */
    class DefaultIdempotencyTokenProvider : IdempotencyTokenProvider {
        override fun generateToken(): String = Instant.now().epochSeconds.toString() + Random.nextInt()
    }

    companion object {
        val Default: IdempotencyTokenProvider = DefaultIdempotencyTokenProvider()
    }
}
