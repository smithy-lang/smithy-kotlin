/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.collections.PeriodicSweepCache
import aws.smithy.kotlin.runtime.hashing.HashSupplier
import aws.smithy.kotlin.runtime.hashing.Sha256
import aws.smithy.kotlin.runtime.hashing.hmac
import aws.smithy.kotlin.runtime.text.encoding.encodeToHex
import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.time.TimestampFormat
import aws.smithy.kotlin.runtime.util.ExpiringValue
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.hours

/**
 * [SignatureCalculator] for the SigV4 ("AWS4-HMAC-SHA256") algorithm
 * @param sha256Provider the [HashSupplier] to use for computing SHA-256 hashes
 */
internal class SigV4SignatureCalculator(override val sha256Provider: HashSupplier = ::Sha256) : BaseSigV4SignatureCalculator(AwsSigningAlgorithm.SIGV4, sha256Provider) {
    private val signingKeyCache = PeriodicSweepCache<SigningKeyCacheKey, ByteArray>(
        minimumSweepPeriod = 24.hours,
    )

    override fun calculate(signingKey: ByteArray, stringToSign: String): String = hmac(signingKey, stringToSign.encodeToByteArray(), sha256Provider).encodeToHex()

    override fun signingKey(config: AwsSigningConfig): ByteArray = runBlocking {
        val date = config.signingDate.format(TimestampFormat.ISO_8601_CONDENSED_DATE)
        val cacheKey = SigningKeyCacheKey(config.credentials.secretAccessKey, config.region, config.service, date)

        signingKeyCache.get(cacheKey) {
            ExpiringValue(deriveKey(it.secretKey, it.date, it.region, it.service), Clock.System.now() + 24.hours)
        }
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

private data class SigningKeyCacheKey(
    val secretKey: String,
    val region: String,
    val service: String,
    val date: String,
)
