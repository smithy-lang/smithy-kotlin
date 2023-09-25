/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class IdentityProviderChainTest {

    private class TestChain(vararg providers: IdentityProvider) : IdentityProviderChain<IdentityProvider, Identity>(*providers)
    private data class TestIdentity(val name: String) : Identity {
        override val expiration: Instant? = null
        override val attributes: Attributes = emptyAttributes()
    }
    private class TestProvider(val name: String? = null) : IdentityProvider {
        override suspend fun resolve(attributes: Attributes): TestIdentity = name?.let { TestIdentity(it) } ?: error("no identity available")
    }

    @Test
    fun testNoProviders() {
        assertFails("at least one provider") {
            TestChain()
        }
    }

    @Test
    fun testChain() = runTest {
        val chain = TestChain(
            TestProvider(null),
            TestProvider("ident1"),
            TestProvider("ident2"),
        )

        assertEquals(TestIdentity("ident1"), chain.resolve())
    }

    @Test
    fun testChainNoIdentity() = runTest {
        val chain = TestChain(
            TestProvider(null),
            TestProvider(null),
        )

        val ex = assertFailsWith<IdentityProviderException> {
            chain.resolve()
        }
        ex.message.shouldContain("No identity could be resolved from the chain: TestChain -> TestProvider -> TestProvider")

        assertEquals(2, ex.suppressedExceptions.size)
    }
}
