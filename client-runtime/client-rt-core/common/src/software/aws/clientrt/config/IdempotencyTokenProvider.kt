/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.config

import software.aws.clientrt.time.Instant
import kotlin.random.Random

/**
 * User-accessible configuration for client-side token generation.
 */
interface IdempotencyTokenConfig {

    /**
     * Allows to supply a custom function generate idempotency tokens.
     */
    val idempotencyTokenProvider: IdempotencyTokenProvider?
}

/**
 * Describes a function and default implementation to produce a string used as a token to dedupe
 * requests from the client.
 */
fun interface IdempotencyTokenProvider {

    /**
     * Generate a unique, UUID-like string that can be used to track unique client-side requests.
     */
    fun generateToken(): String

    companion object {
        /**
         * Creates the default token provider.
         */
        val Default: IdempotencyTokenProvider = DefaultIdempotencyTokenProvider()
    }
}

/**
 * This is the default function to generate a UUID for idempotency tokens if they are not specified
 * in client code.
 *
 * TODO: Implement a real function.  See https://www.pivotaltracker.com/story/show/174214013
 */
private class DefaultIdempotencyTokenProvider : IdempotencyTokenProvider {
    override fun generateToken(): String = Instant.now().epochSeconds.toString() + Random.nextInt()
}
