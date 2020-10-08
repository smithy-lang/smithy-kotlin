package software.aws.clientrt

import software.aws.clientrt.time.Instant
import kotlin.random.Random

typealias IdempotencyTokenProvider = () -> String

/**
 * This is the default function to generate a UUID for idempotency tokens if they are not specified
 * in client code.
 *
 * TODO: Implement a real function.  See https://www.pivotaltracker.com/story/show/174214013
 */
val defaultIdempotencyTokenProvider: IdempotencyTokenProvider = { Instant.now().epochSeconds.toString() + Random.nextInt() }