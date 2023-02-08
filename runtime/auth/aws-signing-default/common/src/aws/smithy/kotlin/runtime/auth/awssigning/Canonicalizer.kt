/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.hashing.HashSupplier
import aws.smithy.kotlin.runtime.hashing.Sha256
import aws.smithy.kotlin.runtime.hashing.hash
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.util.encodeLabel
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.io.internal.SdkDispatchers
import aws.smithy.kotlin.runtime.net.UrlBuilder
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.util.*
import aws.smithy.kotlin.runtime.util.text.*
import kotlinx.coroutines.withContext

/**
 * The data for a canonical request.
 * @param request The [HttpRequestBuilder] with modified/added headers or query parameters
 * @param requestString The canonical request string which is used in signature calculation
 * @param signedHeaders A semicolon-delimited list of signed headers, all lowercase
 * @param hash The hash for the body (either calculated from the body or pre-configured)
 */
internal data class CanonicalRequest(
    val request: HttpRequestBuilder,
    val requestString: String,
    val signedHeaders: String,
    val hash: String,
)

/**
 * An object that can canonicalize a request.
 */
internal interface Canonicalizer {
    companion object {
        /**
         * The default implementation of [Canonicalizer] to use.
         */
        val Default = DefaultCanonicalizer()
    }

    /**
     * Canonicalize a request
     * @param request The [HttpRequest] containing the data ready for signing
     * @param config The signing parameters to use
     * @param credentials Retrieved credentials used to canonicalize the request
     */
    suspend fun canonicalRequest(
        request: HttpRequest,
        config: AwsSigningConfig,
        credentials: Credentials,
    ): CanonicalRequest
}

// Taken from https://github.com/awslabs/aws-c-auth/blob/31d573c0dd328db5775f7a55650d27b8c08311ba/source/aws_signing.c#L118-L151
private val skipHeaders = setOf(
    "connection",
    "sec-websocket-key",
    "sec-websocket-protocol",
    "sec-websocket-version",
    "upgrade",
    "user-agent",
    "x-amzn-trace-id",
)

internal class DefaultCanonicalizer(private val sha256Supplier: HashSupplier = ::Sha256) : Canonicalizer {
    override suspend fun canonicalRequest(
        request: HttpRequest,
        config: AwsSigningConfig,
        credentials: Credentials,
    ): CanonicalRequest {
        val hash = when (val hashSpec = config.hashSpecification) {
            is HashSpecification.CalculateFromPayload -> request.body.calculateHash()
            is HashSpecification.HashLiteral -> hashSpec.hash
        }

        val signViaQueryParams = config.signatureType == AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
        val addHashHeader = !signViaQueryParams && config.signedBodyHeader == AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256
        val sessionToken = credentials.sessionToken?.let { if (signViaQueryParams) it.urlEncodeComponent() else it }

        val builder = request.toBuilder()

        val params = when (config.signatureType) {
            AwsSignatureType.HTTP_REQUEST_VIA_HEADERS -> builder.headers
            AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS -> builder.url.parameters
            else -> TODO("Support for ${config.signatureType} is not yet implemented")
        }
        fun param(name: String, value: String?, predicate: Boolean = true) {
            if (predicate && value != null) params[name] = value
        }

        param("Host", builder.url.host.toString(), !(signViaQueryParams || "Host" in params))
        param("X-Amz-Algorithm", ALGORITHM_NAME, signViaQueryParams)
        param("X-Amz-Credential", credentialValue(config, credentials), signViaQueryParams)
        param("X-Amz-Content-Sha256", hash, addHashHeader)
        param("X-Amz-Date", config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED))
        param("X-Amz-Expires", config.expiresAfter?.inWholeSeconds?.toString(), signViaQueryParams)
        param("X-Amz-Security-Token", sessionToken, !config.omitSessionToken) // Add pre-sig if omitSessionToken=false

        val headers = builder
            .headers
            .entries()
            .asSequence()
            .filter { includeHeader(it.key, config) }
            .map { it.key.lowercase() to it.value }
            .sortedBy { it.first }
        val signedHeaders = headers.joinToString(separator = ";") { it.first }

        param("X-Amz-SignedHeaders", signedHeaders, signViaQueryParams)

        val requestString = buildString {
            appendLine(builder.method.name)
            appendLine(builder.url.canonicalPath(config))
            appendLine(builder.url.canonicalQueryParams())
            headers.map { it.canonicalLine() }.forEach(::appendLine)
            appendLine() // Yes, add an extra blank line after all the headers
            appendLine(signedHeaders)
            append(hash)
        }

        param("X-Amz-Security-Token", sessionToken, config.omitSessionToken) // Add post-sig if omitSessionToken=true

        return CanonicalRequest(builder, requestString, signedHeaders, hash)
    }

    /**
     * Calculate a hash for this [HttpBody]. This method does not support attempting to calculate a hash on a
     * non-replayable stream.
     * @return The hash as a hex string
     */
    private suspend fun HttpBody.calculateHash(): String {
        require(!isOneShot) { "Stream must be replayable to calculate a body hash" }
        return when (this) {
            is HttpBody.Empty -> HashSpecification.EmptyBody.hash
            is HttpBody.Bytes -> bytes().hash(sha256Supplier).encodeToHex()
            is HttpBody.ChannelContent -> {
                val reader = readFrom()
                reader.sha256().encodeToHex()
            }
            is HttpBody.SourceContent -> {
                val source = readFrom()
                withContext(SdkDispatchers.IO) {
                    source.sha256().encodeToHex()
                }
            }
        }
    }

    /**
     * Calculate a hash for this [SdkByteReadChannel].
     * @return The hash as a byte array
     */
    private suspend fun SdkByteReadChannel.sha256(): ByteArray {
        val hash = sha256Supplier()
        val sink = HashingSink(hash)
        readAll(sink)
        return hash.digest()
    }

    private fun SdkSource.sha256(): ByteArray {
        val hash = sha256Supplier()
        val source = this
        val sink = HashingSink(hash)
        sink.buffer().use { bufferedSink ->
            source.use {
                bufferedSink.writeAll(source)
            }
        }
        return hash.digest()
    }
}

/** The number of bytes to read at a time during SHA256 calculation on streaming bodies. */
private const val STREAM_CHUNK_BYTES = 16384 // 16KB

/**
 * Canonicalizes a path from this [UrlBuilder].
 * @param config The signing configuration to use
 * @return The canonicalized path
 */
internal fun UrlBuilder.canonicalPath(config: AwsSigningConfig): String {
    val segmentTransform = if (config.useDoubleUriEncode) { s: String -> s.encodeLabel() } else null
    val pathTransform = if (config.normalizeUriPath) String::normalizePathSegments else String::transformPathSegments
    return pathTransform(path.trim(), segmentTransform)
}

/**
 * Canonicalizes the query parameters from this [UrlBuilder].
 * @return The canonicalized query parameters
 */
private fun UrlBuilder.canonicalQueryParams(): String = parameters
    .entries()
    .map { it.key.urlReencodeComponent() to it.value }
    .sortedBy { it.first }
    .flatMap { it.asQueryParamComponents() }
    .joinToString(separator = "&")

private fun Pair<String, List<String>>.asQueryParamComponents(): List<String> =
    second
        .map { this@asQueryParamComponents.first to it.urlReencodeComponent() }
        .sortedBy { it.second }
        .map { "${it.first}=${it.second}" }

private fun Pair<String, List<String>>.canonicalLine(): String {
    val valuesString = second.joinToString(separator = ",") { it.trimAll() }
    return "$first:$valuesString"
}

private val multipleSpaces = " +".toRegex()
private fun String.trimAll() = replace(multipleSpaces, " ").trim()

private fun includeHeader(name: String, config: AwsSigningConfig): Boolean =
    name.lowercase() !in skipHeaders && config.shouldSignHeader(name)
