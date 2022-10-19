/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning.middleware

import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.auth.awssigning.*
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.tracing.warn
import aws.smithy.kotlin.runtime.tracing.withChildTraceSpan
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.get
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

/**
 * HTTP request pipeline middleware that signs outgoing requests
 */
@InternalApi
public class AwsSigningMiddleware(private val config: Config) : ModifyRequestMiddleware {
    public companion object {
        public inline operator fun invoke(block: Config.() -> Unit): AwsSigningMiddleware {
            val config = Config().apply(block)
            requireNotNull(config.credentialsProvider) { "A credentials provider must be specified for the middleware" }
            requireNotNull(config.service) { "A service must be specified for the middleware" }
            requireNotNull(config.signer) { "A signer must be specified for the middleware" }
            return AwsSigningMiddleware(config)
        }
    }

    public class Config {
        /**
         * The signer implementation to use for signing
         */
        public var signer: AwsSigner? = null

        /**
         * The credentials provider used to sign requests with
         */
        public var credentialsProvider: CredentialsProvider? = null

        /**
         * The credential scope service name to sign requests for
         * NOTE: The operation context is favored when [AwsSigningAttributes.SigningService] is set
         */
        public var service: String? = null

        /**
         * Sets what signature should be computed
         */
        public var signatureType: AwsSignatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS

        /**
         * The algorithm to sign with
         */
        public var algorithm: AwsSigningAlgorithm = AwsSigningAlgorithm.SIGV4

        /**
         * Indicates whether the payload should be unsigned _even_ in cases where it would otherwise be signable (e.g.,
         * a replayable stream or byte buffer). Setting this value to `false` will _not_ allow signing a non-replayable
         * stream.
         */
        public var isUnsignedPayload: Boolean = false

        /**
         * The uri is assumed to be encoded once in preparation for transmission.  Certain services
         * do not decode before checking signature, requiring double-encoding the uri in the canonical
         * request in order to pass a signature check.
         */
        public var useDoubleUriEncode: Boolean = true

        /**
         * Controls whether or not the uri paths should be normalized when building the canonical request
         */
        public var normalizeUriPath: Boolean = true

        /**
         * Flag indicating if the "X-Amz-Security-Token" query param should be omitted.
         * Normally, this parameter is added during signing if the credentials have a session token.
         * The only known case where this should be true is when signing a websocket handshake to IoT Core.
         */
        public var omitSessionToken: Boolean = false

        /**
         * Controls what body "hash" header, if any, should be added to the canonical request and the signed request.
         * Most services do not require this additional header.
         */
        public var signedBodyHeader: AwsSignedBodyHeader = AwsSignedBodyHeader.NONE

        /**
         * If non-zero and the signing transform is query param, then signing will add X-Amz-Expires to the query
         * string, equal to the value specified here.  If this value is zero or if header signing is being used then
         * this parameter has no effect.
         */
        public var expiresAfter: Duration? = null
    }

    override fun install(op: SdkHttpOperation<*, *>) {
        op.execution.finalize.register(this)
    }

    override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest =
        coroutineContext.withChildTraceSpan("Signing") {
            val body = req.subject.body

            // favor attributes from the current request context
            val contextHashSpecification = req.context.getOrNull(AwsSigningAttributes.HashSpecification)
            val contextSignedBodyHeader = req.context.getOrNull(AwsSigningAttributes.SignedBodyHeader)

            // operation signing config is baseConfig + operation specific config/overrides
            val signingConfig = AwsSigningConfig {
                region = req.context[AwsSigningAttributes.SigningRegion]
                service = req.context.getOrNull(AwsSigningAttributes.SigningService) ?: checkNotNull(config.service)
                credentialsProvider = checkNotNull(config.credentialsProvider)
                algorithm = config.algorithm
                signingDate = req.context.getOrNull(AwsSigningAttributes.SigningDate)

                signatureType = config.signatureType
                omitSessionToken = config.omitSessionToken
                normalizeUriPath = config.normalizeUriPath
                useDoubleUriEncode = config.useDoubleUriEncode
                expiresAfter = config.expiresAfter

                signedBodyHeader = contextSignedBodyHeader ?: config.signedBodyHeader

                // SDKs are supposed to default to signed payload _always_ when possible (and when `unsignedPayload`
                // trait isn't present).
                //
                // There are a few escape hatches/special cases:
                //     1. Customer explicitly disables signed payload (via Config.isUnsignedPayload)
                //     2. Customer provides a (potentially) unbounded stream (via HttpBody.Streaming)
                //
                // When an unbounded stream (2) is given we proceed as follows:
                //     2.1. is it replayable?
                //          (2.1.1) yes -> sign the payload (stream can be consumed more than once)
                //          (2.1.2) no -> unsigned payload
                //
                // NOTE: Chunked signing is NOT enabled through this middleware.
                // NOTE: 2.1.2 is handled below

                // FIXME - see: https://github.com/awslabs/smithy-kotlin/issues/296
                // if we know we have a (streaming) body and toSignableRequest() fails to convert it to a CRT equivalent
                // then we must decide how to compute the payload hash ourselves (defaults to unsigned payload)
                hashSpecification = when {
                    contextHashSpecification != null -> contextHashSpecification
                    config.isUnsignedPayload -> HashSpecification.UnsignedPayload
                    body is HttpBody.Empty -> HashSpecification.EmptyBody
                    body is HttpBody.Streaming && !body.isReplayable -> {
                        coroutineContext.warn<AwsSigningMiddleware> {
                            "unable to compute hash for unbounded stream; defaulting to unsigned payload"
                        }
                        HashSpecification.UnsignedPayload
                    }
                    // use the payload to compute the hash
                    else -> HashSpecification.CalculateFromPayload
                }
            }

            val signingResult = checkNotNull(config.signer).sign(req.subject.build(), signingConfig)
            val signedRequest = signingResult.output

            // Add the signature to the request context
            req.context[AwsSigningAttributes.RequestSignature] = signingResult.signature

            req.subject.update(signedRequest)
            req.subject.body.resetStream()

            req
        }
}

private fun HttpBody.resetStream() {
    if (this is HttpBody.Streaming && this.isReplayable) {
        this.reset()
    }
}

private fun HttpRequestBuilder.update(signedRequest: HttpRequest) {
    signedRequest.headers.forEach { key, values ->
        this.headers.appendMissing(key, values)
    }

    signedRequest.url.parameters.forEach { key, values ->
        // The signed request has a URL-encoded path which means simply appending missing could result in both the raw
        // and percent-encoded value being present. Instead, just append new keys added by signing.
        if (!this.url.parameters.contains(key)) {
            url.parameters.appendAll(key, values)
        }
    }
}
