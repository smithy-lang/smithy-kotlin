/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.hashing.HashSupplier
import aws.smithy.kotlin.runtime.hashing.Sha256
import aws.smithy.kotlin.runtime.hashing.hmac
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import aws.smithy.kotlin.runtime.time.TimestampFormat

/**
 * [SignatureCalculator] for the SigV4 ("AWS4-HMAC-SHA256") algorithm
 * @param sha256Provider the [HashSupplier] to use for computing SHA-256 hashes
 */
internal class SigV4SignatureCalculator(override val sha256Provider: HashSupplier = ::Sha256) : BaseSigV4SignatureCalculator(AwsSigningAlgorithm.SIGV4, sha256Provider) {
    override fun calculate(signingKey: ByteArray, stringToSign: String): String =
        hmac(signingKey, stringToSign.encodeToByteArray(), sha256Provider).encodeToHex()

    override fun signingKey(config: AwsSigningConfig): ByteArray {
        fun hmac(key: ByteArray, message: String) = hmac(key, message.encodeToByteArray(), sha256Provider)

        val initialKey = ("AWS4" + config.credentials.secretAccessKey).encodeToByteArray()
        val kDate = hmac(initialKey, config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED_DATE))
        val kRegion = hmac(kDate, config.region)
        val kService = hmac(kRegion, config.service)
        return hmac(kService, "aws4_request")
    }
}
