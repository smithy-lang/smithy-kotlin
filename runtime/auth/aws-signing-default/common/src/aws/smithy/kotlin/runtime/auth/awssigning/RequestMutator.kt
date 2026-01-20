/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.time.TimestampFormat

/**
 * An object that can mutate requests to include signing attributes.
 */
internal interface RequestMutator {
    companion object {
        /**
         * The default implementation of [RequestMutator].
         */
        val Default = DefaultRequestMutator()
    }

    /**
     * Appends authorization information to a canonical request, returning a new request ready to be sent.
     * @param config The signing configuration to use
     * @param canonical The [CanonicalRequest] which has already been modified
     * @param signatureHex The signature as a hex string
     * @return A new [HttpRequest] containing all the relevant signing/authorization attributes which is ready to be
     * sent.
     */
    fun appendAuth(
        config: AwsSigningConfig,
        canonical: CanonicalRequest,
        signatureHex: String,
    ): HttpRequest
}

internal class DefaultRequestMutator : RequestMutator {
    override fun appendAuth(
        config: AwsSigningConfig,
        canonical: CanonicalRequest,
        signatureHex: String,
    ): HttpRequest {
        when (config.signatureType) {
            AwsSignatureType.HTTP_REQUEST_VIA_HEADERS -> {
                val credential = "Credential=${credentialValue(config)}"
                val signedHeaders = "SignedHeaders=${canonical.signedHeaders}"
                val signature = "Signature=$signatureHex"
                canonical.request.headers["Authorization"] = "${config.algorithm.signingName} $credential, $signedHeaders, $signature"
            }

            AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS ->
                canonical.request.url.parameters.decodedParameters.put("X-Amz-Signature", signatureHex)

            else -> TODO("Support for ${config.signatureType} is not yet implemented")
        }

        return canonical.request.build()
    }
}

/**
 * Formats a credential scope consisting of a signing date, region (SigV4 only), service, and a signature type
 */
internal val AwsSigningConfig.credentialScope: String
    get() {
        val signingDate = signingDate.format(TimestampFormat.ISO_8601_CONDENSED_DATE)
        return when (algorithm) {
            AwsSigningAlgorithm.SIGV4 -> "$signingDate/$region/$service/aws4_request"
            AwsSigningAlgorithm.SIGV4_ASYMMETRIC -> "$signingDate/$service/aws4_request"
        }
    }

/**
 * Formats the value for a credential header/parameter
 */
internal fun credentialValue(config: AwsSigningConfig): String = "${config.credentials.accessKeyId}/${config.credentialScope}"
