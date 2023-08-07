/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BearerTokenProviderChainTest {
    private class TestTokenProvider(val token: String? = null) : BearerTokenProvider {
        override suspend fun resolve(attributes: Attributes): BearerToken =
            if (token != null) Token(token) else error("no credentials available")
    }

    @Test
    fun testVararg() = runTest {
        val chain = BearerTokenProviderChain(TestTokenProvider(), TestTokenProvider("token"))
        assertEquals(Token("token"), chain.resolve())
    }

    @Test
    fun testList() = runTest {
        val tokenProviders = listOf<BearerTokenProvider>(TestTokenProvider(), TestTokenProvider("token"))
        val chain = BearerTokenProviderChain(tokenProviders)
        assertEquals(Token("token"), chain.resolve())
    }
}

private data class Token(
    override val token: String,
    override val attributes: Attributes = emptyAttributes(),
    override val expiration: Instant? = null,
) : BearerToken
