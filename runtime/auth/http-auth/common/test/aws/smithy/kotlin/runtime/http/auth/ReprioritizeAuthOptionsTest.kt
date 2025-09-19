/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.http.auth.reprioritizeAuthOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReprioritizeAuthOptionsTest {
    @Test
    fun testReprioritizeAuthOptions() = runTest {
        val preference = listOf(AuthSchemeId.AwsSigV4Asymmetric)
        val authOptions = listOf(
            AuthOption(AuthSchemeId.AwsSigV4),
            AuthOption(AuthSchemeId.AwsSigV4Asymmetric),
        )

        val expected = listOf(
            AuthOption(AuthSchemeId.AwsSigV4Asymmetric),
            AuthOption(AuthSchemeId.AwsSigV4),
        )

        assertEquals(expected, reprioritizeAuthOptions(preference, authOptions))
    }

    @Test
    fun testReprioritizeAuthOptionsNoOp() = runTest {
        val preference = listOf(AuthSchemeId.AwsSigV4)
        val authOptions = listOf(
            AuthOption(AuthSchemeId.AwsSigV4),
            AuthOption(AuthSchemeId.AwsSigV4Asymmetric),
        )

        val expected = listOf(
            AuthOption(AuthSchemeId.AwsSigV4),
            AuthOption(AuthSchemeId.AwsSigV4Asymmetric),
        )

        assertEquals(expected, reprioritizeAuthOptions(preference, authOptions))
    }

    @Test
    fun testReprioritizeAuthOptionsAllPreferred() = runTest {
        val preference = listOf(AuthSchemeId.HttpDigest, AuthSchemeId.HttpBasic)
        val authOptions = listOf(
            AuthOption(AuthSchemeId.HttpBasic),
            AuthOption(AuthSchemeId.HttpDigest),
        )

        val expected = listOf(
            AuthOption(AuthSchemeId.HttpDigest),
            AuthOption(AuthSchemeId.HttpBasic),
        )

        assertEquals(expected, reprioritizeAuthOptions(preference, authOptions))
    }

    @Test
    fun testReprioritizeAuthOptionsNoPreference() = runTest {
        val preference = emptyList<AuthSchemeId>()
        val authOptions = listOf(
            AuthOption(AuthSchemeId.AwsSigV4),
            AuthOption(AuthSchemeId.HttpBearer),
        )

        val expected = authOptions

        assertEquals(expected, reprioritizeAuthOptions(preference, authOptions))
    }

    @Test
    fun testReprioritizeAuthOptionsEmptyAuthOptions() = runTest {
        val preference = listOf(AuthSchemeId.AwsSigV4)
        val authOptions = emptyList<AuthOption>()

        val expected = emptyList<AuthOption>()

        assertEquals(expected, reprioritizeAuthOptions(preference, authOptions))
    }
}
