/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.config

import io.kotest.matchers.string.shouldMatch
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

    @Test
    fun defaultIdempotencyTokenProviderReturnsUuid() {
        val token = IdempotencyTokenProvider.Default.generateToken()
        token.shouldMatch("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
