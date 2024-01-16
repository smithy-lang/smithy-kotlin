/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.hashing.HashSupplier
import aws.smithy.kotlin.runtime.hashing.Sha256
import aws.smithy.kotlin.runtime.hashing.hash
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.io.internal.SdkDispatchers
import aws.smithy.kotlin.runtime.net.url.QueryParameters
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.net.url.UrlPath
import aws.smithy.kotlin.runtime.text.encoding.Encodable
import aws.smithy.kotlin.runtime.text.encoding.PercentEncoding
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import aws.smithy.kotlin.runtime.time.TimestampFormat
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
     */
    suspend fun canonicalRequest(
        request: HttpRequest,
        config: AwsSigningConfig,
    ): CanonicalRequest
}

// Taken from https://github.com/awslabs/aws-c-auth/blob/dd505b55fd46222834f35c6e54165d8cbebbfaaa/source/aws_signing.c#L118-L156
private val skipHeaders = setOf(
    "connection",
    "expect", // https://github.com/awslabs/aws-sdk-kotlin/issues/862
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
    ): CanonicalRequest {
        val hash = when (val hashSpec = config.hashSpecification) {
            is HashSpecification.CalculateFromPayload -> request.body.calculateHash()
            is HashSpecification.HashLiteral -> hashSpec.hash
        }

        val signViaQueryParams = config.signatureType == AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
        val addHashHeader = !signViaQueryParams && config.signedBodyHeader == AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256
        val sessionToken = config.credentials.sessionToken

        val builder = request.toBuilder()

        fun param(name: String, value: String?, predicate: Boolean = true, overwrite: Boolean = true) {
            if (predicate && value != null) {
                when (config.signatureType) {
                    AwsSignatureType.HTTP_REQUEST_VIA_HEADERS -> {
                        if (overwrite || name !in builder.headers) builder.headers[name] = value
                    }

                    AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS -> {
                        val params = builder.url.parameters

                        if (overwrite || name !in params.decodedParameters) {
                            // Remove existing entry because we're using a different encoding
                            params.decodedParameters.remove(name)

                            params.put(
                                PercentEncoding.SigV4.encodableFromDecoded(name),
                                PercentEncoding.SigV4.encodableFromDecoded(value),
                            )
                        }
                    }

                    else -> TODO("Support for ${config.signatureType} is not yet implemented")
                }
            }
        }

        param("Host", builder.url.hostAndPort, !signViaQueryParams, overwrite = false)
        param("X-Amz-Algorithm", ALGORITHM_NAME, signViaQueryParams)
        param("X-Amz-Credential", credentialValue(config), signViaQueryParams)
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
 * Canonicalizes a path from this [Url.Builder].
 * @param config The signing configuration to use
 * @return The canonicalized path
 */
internal fun Url.Builder.canonicalPath(config: AwsSigningConfig): String {
    val srcPath = path
    val srcSegments = srcPath.segments

    val mapper: (Encodable) -> Encodable = when (config.useDoubleUriEncode) {
        true -> { existing ->
            // This is _double_ encoding so treat the existing encoded output as "decoded" for the purposes re-encoding
            PercentEncoding.SigV4.encodableFromDecoded(existing.encoded)
        }
        else -> {
            // This is _single_ encoding and the data are already encoded—just pass it straight through
            { it }
        }
    }

    return UrlPath {
        srcSegments.mapTo(segments, mapper)
        trailingSlash = srcPath.trailingSlash
        if (config.normalizeUriPath) normalize()
    }.toString()
}

/**
 * Canonicalizes the query parameters from this [Url.Builder].
 * @return The canonicalized query parameters
 */
internal fun Url.Builder.canonicalQueryParams(): String = QueryParameters {
    parameters
        .entries
        .associate { (key, values) ->
            val reencodedKey = key.reencode(PercentEncoding.SigV4)
            val reencodedValues = values.map { it.reencode(PercentEncoding.SigV4) }
            reencodedKey to reencodedValues
        }
        .entries
        .sortedWith(compareBy { it.key.encoded }) // Sort keys
        .associateTo(this) { (key, values) ->
            key to values.sortedWith(compareBy { it.encoded }).toMutableList() // Sort values
        }
}.toString().removePrefix("?")

private fun Pair<String, List<String>>.canonicalLine(): String {
    val valuesString = second.joinToString(separator = ",") { it.trimAll() }
    return "$first:$valuesString"
}

private val multipleSpaces = " +".toRegex()
private fun String.trimAll() = replace(multipleSpaces, " ").trim()

private fun includeHeader(name: String, config: AwsSigningConfig): Boolean =
    name.lowercase() !in skipHeaders && config.shouldSignHeader(name)
