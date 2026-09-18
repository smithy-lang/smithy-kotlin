/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.time.Instant
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

    private class RecordingProvider(
        private val failOnInvalidate: Boolean = false,
    ) : IdentityProvider {
        val invalidated = mutableListOf<Identity>()

        override suspend fun resolve(attributes: Attributes): TestIdentity = TestIdentity("recorded")

        override suspend fun invalidate(rejectedIdentity: Identity) {
            invalidated.add(rejectedIdentity)
            if (failOnInvalidate) error("this provider cannot invalidate")
        }
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

    @Test
    fun testInvalidateReachesEveryProvider() = runTest {
        // The chain does not know which provider supplied the identity - a profile file can be re-read between
        // resolutions - so every member is told and each decides whether it holds the rejected value.
        val first = RecordingProvider()
        val second = RecordingProvider()
        val chain = TestChain(first, second)
        val rejected = TestIdentity("rejected")

        chain.invalidate(rejected)

        assertEquals(listOf<Identity>(rejected), first.invalidated)
        assertEquals(listOf<Identity>(rejected), second.invalidated)
    }

    @Test
    fun testOneProviderFailingDoesNotPreventTheOthersBeingTold() = runTest {
        val failing = RecordingProvider(failOnInvalidate = true)
        val healthy = RecordingProvider()
        val chain = TestChain(failing, healthy)
        val rejected = TestIdentity("rejected")

        chain.invalidate(rejected)

        assertEquals(listOf<Identity>(rejected), healthy.invalidated)
    }

    @Test
    fun testInvalidateIsANoOpForProvidersThatDoNotCache() = runTest {
        // the default body does nothing, which is correct for a provider with nothing cached to mark
        TestChain(TestProvider("ident1")).invalidate(TestIdentity("rejected"))
    }
}
