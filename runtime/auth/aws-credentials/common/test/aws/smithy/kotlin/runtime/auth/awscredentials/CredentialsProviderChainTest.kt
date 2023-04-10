/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.util.Attributes
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialsProviderChainTest {
    @Test
    fun testNoProviders() {
        assertFails("at least one provider") {
            CredentialsProviderChain()
        }
    }
    data class TestProvider(val credentials: Credentials? = null) : CredentialsProvider {
        override suspend fun resolve(attributes: Attributes): Credentials = credentials ?: throw IllegalStateException("no credentials available")
    }

    @Test
    fun testChain() = runTest {
        val chain = CredentialsProviderChain(
            TestProvider(null),
            TestProvider(Credentials("akid1", "secret1")),
            TestProvider(Credentials("akid2", "secret2")),
        )

        assertEquals(Credentials("akid1", "secret1"), chain.resolve())
    }

    @Test
    fun testChainNoCredentials() = runTest {
        val chain = CredentialsProviderChain(
            TestProvider(null),
            TestProvider(null),
        )

        val ex = assertFailsWith<CredentialsProviderException> {
            chain.resolve()
        }
        ex.message.shouldContain("No credentials could be loaded from the chain: CredentialsProviderChain -> TestProvider -> TestProvider")

        assertEquals(2, ex.suppressedExceptions.size)
    }
}
