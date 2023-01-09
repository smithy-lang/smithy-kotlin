/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.util.Uuid
import aws.smithy.kotlin.runtime.util.Uuid.WeakRng

/**
 * User-accessible configuration for client-side token generation.
 */
public interface IdempotencyTokenConfig {

    /**
     * Allows to supply a custom function generate idempotency tokens.
     */
    public val idempotencyTokenProvider: IdempotencyTokenProvider?
}

/**
 * Describes a function and default implementation to produce a string used as a token to dedupe
 * requests from the client.
 */
public fun interface IdempotencyTokenProvider {

    /**
     * Generate a unique, UUID-like string that can be used to track unique client-side requests.
     */
    public fun generateToken(): String

    public companion object {
        /**
         * Creates the default token provider.
         */
        public val Default: IdempotencyTokenProvider = DefaultIdempotencyTokenProvider()
    }
}

/**
 * This is the default function to generate a UUID for idempotency tokens if they are not specified
 * in client code.
 */
private class DefaultIdempotencyTokenProvider : IdempotencyTokenProvider {
    @OptIn(WeakRng::class)
    override fun generateToken(): String = Uuid.random().toString()
}
