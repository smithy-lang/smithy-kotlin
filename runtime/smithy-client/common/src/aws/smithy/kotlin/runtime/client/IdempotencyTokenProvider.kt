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
     * The [IdempotencyTokenProvider] used to generate idempotency tokens.
     */
    public val idempotencyTokenProvider: IdempotencyTokenProvider

    /**
     * Configure the [IdempotencyTokenProvider] used by SDK clients to generate idempotency tokens.
     */
    public interface Builder {
        /**
         * Override the default idempotency token generator. SDK clients will generate tokens for members
         * that represent idempotent tokens when not explicitly set by the caller using this generator.
         */
        public var idempotencyTokenProvider: IdempotencyTokenProvider?
    }
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
