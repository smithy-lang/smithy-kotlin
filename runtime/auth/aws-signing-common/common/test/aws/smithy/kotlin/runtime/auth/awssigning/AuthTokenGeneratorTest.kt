/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.ManualClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

class AuthTokenGeneratorTest {
    @Test
    fun testGenerateAuthToken() = runTest {
        val credentials = Credentials("akid", "secret")

        val credentialsProvider = object : CredentialsProvider {
            var credentialsResolved = false
            override suspend fun resolve(attributes: Attributes): Credentials {
                credentialsResolved = true
                return credentials
            }
        }

        val clock = ManualClock(Instant.fromEpochSeconds(0))

        val generator = AuthTokenGenerator("foo", credentialsProvider, TEST_SIGNER, clock = clock)

        val endpoint = Url { host = Host.parse("foo.bar.us-east-1.baz") }
        val token = generator.generateAuthToken(endpoint, "us-east-1", 333.seconds)

        assertContains(token, "foo.bar.us-east-1.baz")
        assertContains(token, "X-Amz-Credential=signature") // test custom signer was invoked
        assertContains(token, "X-Amz-Expires=333") // expiration
        assertContains(token, "X-Amz-SigningDate=0") // clock
        assertContains(token, "X-Amz-SignedHeaders=host")

        assertTrue(credentialsProvider.credentialsResolved)
    }
}

private val TEST_SIGNER = object : AwsSigner {
    override suspend fun sign(
        request: HttpRequest,
        config: AwsSigningConfig,
    ): AwsSigningResult<HttpRequest> {
        val builder = request.toBuilder()
        builder.url.parameters.decodedParameters.apply {
            put("X-Amz-Credential", "signature")
            put("X-Amz-Expires", (config.expiresAfter?.toLong(DurationUnit.SECONDS) ?: 900).toString())
            put("X-Amz-SigningDate", config.signingDate.epochSeconds.toString())
            put("X-Amz-SignedHeaders", request.headers.names().map { it.lowercase() }.joinToString())
        }

        return AwsSigningResult<HttpRequest>(builder.build(), "signature".encodeToByteArray())
    }

    override suspend fun signChunk(chunkBody: ByteArray, prevSignature: ByteArray, config: AwsSigningConfig): AwsSigningResult<Unit> = throw IllegalStateException("signChunk unexpectedly invoked")

    override suspend fun signChunkTrailer(trailingHeaders: Headers, prevSignature: ByteArray, config: AwsSigningConfig): AwsSigningResult<Unit> = throw IllegalStateException("signChunkTrailer unexpectedly invoked")
}
