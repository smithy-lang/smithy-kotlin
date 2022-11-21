/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning.tests

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.*
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.Protocol
import aws.smithy.kotlin.runtime.http.Url
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.net.Host
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// based on: https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-streaming.html#example-signature-calculations-streaming
private const val CHUNKED_ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE"
private const val CHUNKED_SECRET_ACCESS_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
private val CHUNKED_TEST_CREDENTIALS_PROVIDER =
    Credentials(CHUNKED_ACCESS_KEY_ID, CHUNKED_SECRET_ACCESS_KEY).asStaticProvider()
private const val CHUNKED_TEST_REGION = "us-east-1"
private const val CHUNKED_TEST_SERVICE = "s3"
private const val CHUNKED_TEST_SIGNING_TIME = "2013-05-24T00:00:00Z"
private const val CHUNK1_SIZE = 65536
private const val CHUNK2_SIZE = 1024

private const val EXPECTED_CHUNK_REQUEST_AUTHORIZATION_HEADER =
    "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
        "SignedHeaders=content-encoding;content-length;host;x-amz-content-sha256;x-amz-date;x-amz-decoded-content-length;x-" +
        "amz-storage-class, Signature=4f232c4386841ef735655705268965c44a0e4690baa4adea153f7db9fa80a0a9"

private const val EXPECTED_REQUEST_SIGNATURE = "4f232c4386841ef735655705268965c44a0e4690baa4adea153f7db9fa80a0a9"
private const val EXPECTED_FIRST_CHUNK_SIGNATURE = "ad80c730a21e5b8d04586a2213dd63b9a0e99e0e2307b0ade35a65485a288648"
private const val EXPECTED_SECOND_CHUNK_SIGNATURE = "0055627c9e194cb4542bae2aa5492e3c1575bbb81b612b7d234b86a503ef5497"
private const val EXPECTED_FINAL_CHUNK_SIGNATURE = "b6c6ea8a5354eaf15b3cb7646744f4275b71ea724fed81ceb9323e279d449df9"

@Suppress("HttpUrlsUsage")
@OptIn(ExperimentalCoroutinesApi::class)
public abstract class BasicSigningTestBase : HasSigner {
    @Test
    public fun testSignRequestSigV4(): TestResult = runTest {
        // sanity test
        val request = HttpRequestBuilder().apply {
            method = HttpMethod.POST
            url.scheme = Protocol.HTTP
            url.host = Host.Domain("demo.us-east-1.amazonaws.com")
            url.path = "/"
            headers.append("Host", "demo.us-east-1.amazonaws.com")
            headers.appendAll("x-amz-archive-description", listOf("test", "test"))
            val requestBody = "{\"TableName\": \"foo\"}"
            body = ByteArrayContent(requestBody.encodeToByteArray())
            headers.append("Content-Length", body.contentLength?.toString() ?: "0")
        }.build()

        val config = AwsSigningConfig {
            region = "us-east-1"
            service = "demo"
            signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
            signingDate = Instant.fromIso8601("2020-10-16T19:56:00Z")
            credentialsProvider = testCredentialsProvider
        }

        val result = signer.sign(request, config)

        val expectedDate = "20201016T195600Z"
        val expectedSig = "e60a4adad4ae15e05c96a0d8ac2482fbcbd66c88647c4457db74e4dad1648608"
        val expectedAuth = "AWS4-HMAC-SHA256 Credential=AKID/20201016/us-east-1/demo/aws4_request, " +
            "SignedHeaders=content-length;host;x-amz-archive-description;x-amz-date;x-amz-security-token, " +
            "Signature=$expectedSig"

        assertEquals(expectedDate, result.output.headers["X-Amz-Date"])
        assertEquals(expectedAuth, result.output.headers["Authorization"])
        assertEquals(expectedSig, result.signature.decodeToString())
    }

    @Test
    public open fun testSignRequestSigV4Asymmetric(): TestResult = runTest {
        // sanity test
        val request = HttpRequestBuilder().apply {
            method = HttpMethod.POST
            url.scheme = Protocol.HTTP
            url.host = Host.Domain("demo.us-east-1.amazonaws.com")
            url.path = "/"
            headers.append("Host", "demo.us-east-1.amazonaws.com")
            headers.appendAll("x-amz-archive-description", listOf("test", "test"))
            val requestBody = "{\"TableName\": \"foo\"}"
            body = ByteArrayContent(requestBody.encodeToByteArray())
            headers.append("Content-Length", body.contentLength?.toString() ?: "0")
        }.build()

        val config = AwsSigningConfig {
            region = "us-east-1"
            service = "service"
            signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC
            signingDate = Instant.fromIso8601("2015-08-30T12:36:00Z")
            credentialsProvider = testCredentialsProvider
        }

        val result = signer.sign(request, config)

        val expectedPrefix = "AWS4-ECDSA-P256-SHA256 Credential=AKID/20150830/service/aws4_request, SignedHeaders=content-length;host;x-amz-archive-description;x-amz-date;x-amz-region-set;x-amz-security-token, Signature="
        val authHeader = result.output.headers["Authorization"]!!
        assertTrue(authHeader.contains(expectedPrefix), "Sigv4A auth header: $authHeader")
    }

    private fun createChunkedRequestSigningConfig(): AwsSigningConfig = AwsSigningConfig {
        algorithm = AwsSigningAlgorithm.SIGV4
        signatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
        region = CHUNKED_TEST_REGION
        service = CHUNKED_TEST_SERVICE
        signingDate = Instant.fromIso8601(CHUNKED_TEST_SIGNING_TIME)
        useDoubleUriEncode = false
        normalizeUriPath = true
        signedBodyHeader = AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256
        hashSpecification = HashSpecification.StreamingAws4HmacSha256Payload
        credentialsProvider = CHUNKED_TEST_CREDENTIALS_PROVIDER
    }

    private fun createChunkedSigningConfig(): AwsSigningConfig = AwsSigningConfig {
        algorithm = AwsSigningAlgorithm.SIGV4
        signatureType = AwsSignatureType.HTTP_REQUEST_CHUNK
        region = CHUNKED_TEST_REGION
        service = CHUNKED_TEST_SERVICE
        signingDate = Instant.fromIso8601(CHUNKED_TEST_SIGNING_TIME)
        useDoubleUriEncode = false
        normalizeUriPath = true
        signedBodyHeader = AwsSignedBodyHeader.NONE
        credentialsProvider = CHUNKED_TEST_CREDENTIALS_PROVIDER
    }

    private fun createChunkedTestRequest() = HttpRequest {
        method = HttpMethod.PUT
        url(Url.parse("https://s3.amazonaws.com/examplebucket/chunkObject.txt"))
        headers {
            set("Host", url.host.toString())
            set("x-amz-storage-class", "REDUCED_REDUNDANCY")
            set("Content-Encoding", "aws-chunked")
            set("x-amz-decoded-content-length", "66560")
            set("Content-Length", "66824")
        }
    }

    private fun chunk1(): ByteArray {
        val chunk = ByteArray(CHUNK1_SIZE)
        for (i in chunk.indices) {
            chunk[i] = 'a'.code.toByte()
        }
        return chunk
    }

    private fun chunk2(): ByteArray {
        val chunk = ByteArray(CHUNK2_SIZE)
        for (i in chunk.indices) {
            chunk[i] = 'a'.code.toByte()
        }
        return chunk
    }

    @Test
    public fun testSignChunks(): TestResult = runTest {
        val request = createChunkedTestRequest()
        val chunkedRequestConfig = createChunkedRequestSigningConfig()
        val requestResult = signer.sign(request, chunkedRequestConfig)
        assertEquals(EXPECTED_CHUNK_REQUEST_AUTHORIZATION_HEADER, requestResult.output.headers["Authorization"])
        assertEquals(EXPECTED_REQUEST_SIGNATURE, requestResult.signature.decodeToString())

        var prevSignature = requestResult.signature

        val chunkedSigningConfig = createChunkedSigningConfig()
        val chunk1Result = signer.signChunk(chunk1(), prevSignature, chunkedSigningConfig)
        assertEquals(EXPECTED_FIRST_CHUNK_SIGNATURE, chunk1Result.signature.decodeToString())
        prevSignature = chunk1Result.signature

        val chunk2Result = signer.signChunk(chunk2(), prevSignature, chunkedSigningConfig)
        assertEquals(EXPECTED_SECOND_CHUNK_SIGNATURE, chunk2Result.signature.decodeToString())
        prevSignature = chunk2Result.signature

        // TODO - do we want 0 byte data like this or just allow null?
        val finalChunkResult = signer.signChunk(ByteArray(0), prevSignature, chunkedSigningConfig)
        assertEquals(EXPECTED_FINAL_CHUNK_SIGNATURE, finalChunkResult.signature.decodeToString())
    }
}
