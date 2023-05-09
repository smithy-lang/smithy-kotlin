/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.identity.Token
import aws.smithy.kotlin.runtime.net.Scheme
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class BearerTokenSignerTest {
    @Test
    fun testAuthorizationHeader() = runTest {
        val signer = BearerTokenSigner()

        val identity = object : Token {
            override val token: String = "mytoken"
            override val attributes: Attributes = emptyAttributes()
            override val expiration: Instant? = null
        }

        val signingRequest = SignHttpRequest(
            HttpRequestBuilder().apply { url.scheme = Scheme.HTTPS },
            identity,
            emptyAttributes(),
        )

        signer.sign(signingRequest)

        assertEquals("Bearer mytoken", signingRequest.httpRequest.headers["Authorization"])
    }

    @Test
    fun testHttpsRequired() = runTest {
        val signer = BearerTokenSigner()

        val identity = object : Token {
            override val token: String = "mytoken"
            override val attributes: Attributes = emptyAttributes()
            override val expiration: Instant? = null
        }

        val signingRequest = SignHttpRequest(
            HttpRequestBuilder().apply { url.scheme = Scheme.HTTP },
            identity,
            emptyAttributes(),
        )

        val ex = assertFailsWith<IllegalStateException> {
            signer.sign(signingRequest)
        }

        ex.message.shouldContain("https is required")
    }
}
