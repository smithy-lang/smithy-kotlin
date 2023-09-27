/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.util.Attributes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CredentialsProviderChainTest {
    private class TestCredentialsProvider(val accessKeyId: String? = null, val secretAccessKey: String? = null) : CredentialsProvider {
        override suspend fun resolve(attributes: Attributes): Credentials =
            if (accessKeyId != null && secretAccessKey != null) Credentials(accessKeyId, secretAccessKey) else error("no credentials available")
    }

    @Test
    fun testVararg() = runTest {
        val chain = CredentialsProviderChain(TestCredentialsProvider(), TestCredentialsProvider("AKeyID", "SecretKey"))
        assertEquals(Credentials("AKeyID", "SecretKey"), chain.resolve())
    }

    @Test
    fun testList() = runTest {
        val credentialsProviders = listOf<CredentialsProvider>(TestCredentialsProvider(), TestCredentialsProvider("AKeyID", "SecretKey"))
        val chain = CredentialsProviderChain(credentialsProviders)
        assertEquals(Credentials("AKeyID", "SecretKey"), chain.resolve())
    }
}
