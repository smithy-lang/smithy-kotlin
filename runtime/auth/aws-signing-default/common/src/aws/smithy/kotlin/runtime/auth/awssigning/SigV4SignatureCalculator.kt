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
import kotlin.concurrent.Volatile

/**
 * [SignatureCalculator] for the SigV4 ("AWS4-HMAC-SHA256") algorithm
 * @param sha256Provider the [HashSupplier] to use for computing SHA-256 hashes
 */
internal class SigV4SignatureCalculator(override val sha256Provider: HashSupplier = ::Sha256) : BaseSigV4SignatureCalculator(AwsSigningAlgorithm.SIGV4, sha256Provider) {
    @Volatile
    private var cachedKey: CachedSigningKey? = null

    override fun calculate(signingKey: ByteArray, stringToSign: String): String = hmac(signingKey, stringToSign.encodeToByteArray(), sha256Provider).encodeToHex()

    override fun signingKey(config: AwsSigningConfig): ByteArray {
        val date = config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED_DATE)
        val secretKey = config.credentials.secretAccessKey
        val region = config.region
        val service = config.service

        cachedKey?.let { cached ->
            if (cached.date == date && cached.secretKey == secretKey && cached.region == region && cached.service == service) {
                return cached.key
            }
        }

        val key = deriveKey(secretKey, date, region, service)
        cachedKey = CachedSigningKey(key, secretKey, region, service, date)
        return key
    }

    private fun deriveKey(secretKey: String, date: String, region: String, service: String): ByteArray {
        fun hmac(key: ByteArray, message: String) = hmac(key, message.encodeToByteArray(), sha256Provider)

        val initialKey = ("AWS4$secretKey").encodeToByteArray()
        val kDate = hmac(initialKey, date)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, service)
        return hmac(kService, "aws4_request")
    }
}

private class CachedSigningKey(
    val key: ByteArray,
    val secretKey: String,
    val region: String,
    val service: String,
    val date: String,
)