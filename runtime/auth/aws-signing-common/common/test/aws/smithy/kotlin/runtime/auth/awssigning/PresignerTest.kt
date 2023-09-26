/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.operation.EndpointResolver
import aws.smithy.kotlin.runtime.http.operation.ResolveEndpointRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.net.Url
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.util.Attributes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val NON_HTTPS_URL = "http://localhost:8080/path/to/resource?foo=bar"

class PresignerTest {
    // Verify that custom endpoint URL schemes aren't changed.
    // See https://github.com/awslabs/aws-sdk-kotlin/issues/938
    @Test
    fun testSignedUrlAllowsHttp() = testSigningUrl("http://localhost:8080/path/to/resource?foo=bar")

    // Verify that custom endpoint URL schemes aren't changed.
    // See https://github.com/awslabs/aws-sdk-kotlin/issues/938
    @Test
    fun testSignedUrlAllowsHttps() = testSigningUrl("https://localhost:8088/path/to/resource?bar=foo")

    private fun testSigningUrl(url: String) = runTest {
        val expectedUrl = Url.parse(url)

        val unsignedRequestBuilder = HttpRequestBuilder()
        val ctx = ExecutionContext()
        val credentialsProvider = TestCredentialsProvider(Credentials("foo", "bar"))
        val endpointResolver = TestEndpointResolver(Endpoint(expectedUrl))
        val signer = TestSigner(HttpRequest { url(expectedUrl) })
        val signingConfig: AwsSigningConfig.Builder.() -> Unit = {
            service = "launch-service"
            region = "the-moon"
        }

        val presignedRequest = presignRequest(
            unsignedRequestBuilder,
            ctx,
            credentialsProvider,
            endpointResolver,
            signer,
            signingConfig,
        )

        val actualUrl = presignedRequest.url

        assertEquals(expectedUrl.scheme, actualUrl.scheme)
        assertEquals(expectedUrl.host, actualUrl.host)
        assertEquals(expectedUrl.port, actualUrl.port)
        assertEquals(expectedUrl.path, actualUrl.path)
        expectedUrl.parameters.forEach { key, value ->
            assertEquals(value, actualUrl.parameters.getAll(key))
        }
    }
}

private class TestCredentialsProvider(private val credentials: Credentials) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials = credentials
}

private class TestEndpointResolver(private val resolvedEndpoint: Endpoint) : EndpointResolver {
    override suspend fun resolve(request: ResolveEndpointRequest): Endpoint = resolvedEndpoint
}

private class TestSigner(private val signedOutput: HttpRequest) : AwsSigner {
    override suspend fun sign(request: HttpRequest, config: AwsSigningConfig): AwsSigningResult<HttpRequest> =
        AwsSigningResult(signedOutput, byteArrayOf())

    override suspend fun signChunk(
        chunkBody: ByteArray,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult<Unit> = error("Method should not be called")

    override suspend fun signChunkTrailer(
        trailingHeaders: Headers,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult<Unit> = error("Method should not be called")
}
