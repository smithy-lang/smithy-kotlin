/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning.standard

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSignatureType
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.util.text.urlReencodeComponent

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
     * @param credentials The retrieved credentials used in the signing process
     * @param signatureHex The signature as a hex string
     * @return A new [HttpRequest] containing all the relevant signing/authorization attributes which is ready to be
     * sent.
     */
    fun appendAuth(
        config: AwsSigningConfig,
        canonical: CanonicalRequest,
        credentials: Credentials,
        signatureHex: String,
    ): HttpRequest
}

internal class DefaultRequestMutator : RequestMutator {
    override fun appendAuth(
        config: AwsSigningConfig,
        canonical: CanonicalRequest,
        credentials: Credentials,
        signatureHex: String,
    ): HttpRequest {
        when (config.signatureType) {
            AwsSignatureType.HTTP_REQUEST_VIA_HEADERS -> {
                val credential = "Credential=${credentialValue(config, credentials)}"
                val signedHeaders = "SignedHeaders=${canonical.signedHeaders}"
                val signature = "Signature=$signatureHex"
                canonical.request.headers["Authorization"] = "$ALGORITHM_NAME $credential, $signedHeaders, $signature"
            }

            AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS -> {
                with(canonical.request.url.parameters) {
                    set("X-Amz-Signature", signatureHex)

                    entries().forEach {
                        remove(it.key)
                        appendAll(it.key, it.value.map(String::urlReencodeComponent))
                    }
                }
            }

            else -> TODO("Not yet implemented")
        }

        return canonical.request.build()
    }
}
