/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.ErrorMetadata
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.ManualClock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the cross-SDK credential refresh test vectors ([CREDENTIAL_REFRESH_TEST_VECTORS]) against
 * [ResilientCachingCredentialsProvider].
 *
 * The abstract starting states are set up by installing credentials with a two-hour lifetime (so a 60 minute advisory
 * window and the default 1 minute mandatory window), then moving the clock into the window the case names. Every
 * credential the source returns declares static stability, as the in-scope providers' credentials do.
 */
class CredentialRefreshVectorTest {
    private val epoch = Instant.fromEpochSeconds(1_700_000_000)
    private val seedLifetime = 2.hours
    private val defaultFreshLifetime = 2.hours

    /** A source answered by the step that expects it to be contacted. */
    private class ScriptedSource(private val clock: ManualClock) : CredentialsProvider {
        var calls = 0
        var next: (() -> Credentials)? = null
        var unexpectedCall = false

        override suspend fun resolve(attributes: Attributes): Credentials {
            calls++
            val answer = next ?: run {
                unexpectedCall = true
                throw ClientException("the source was contacted on a step that did not expect it")
            }
            next = null
            return answer()
        }

        fun credentials(accessKeyId: String, expiresIn: Duration) = Credentials(
            accessKeyId = accessKeyId,
            secretAccessKey = "secret",
            expiration = clock.now() + expiresIn,
        ).copy(attributes = attributesWithStaticStability())

        private fun attributesWithStaticStability() = aws.smithy.kotlin.runtime.collections.attributesOf {
            CredentialsRefreshBehaviorKey to CredentialsRefreshBehavior.RefreshableWithStaticStability
        }
    }

    @Test
    fun testVectors() = runTest {
        val cases = Json.parseToJsonElement(CREDENTIAL_REFRESH_TEST_VECTORS).jsonArray.map { it.jsonObject }
        assertEquals(24, cases.size, "unexpected number of test cases; was the vector file updated?")
        cases.forEach { runCase(it) }
    }

    private suspend fun runCase(case: JsonObject) {
        val id = case.string("id")
        val given = case["given"]!!.jsonObject
        val clock = ManualClock(epoch)
        val source = ScriptedSource(clock)
        val backoff = given["refreshBackoffSeconds"]?.jsonPrimitive?.int?.seconds ?: 7.minutes
        val provider = ResilientCachingCredentialsProvider(
            source = source,
            configuredAdvisoryWindow = given["configuredAdvisoryWindowSeconds"]?.jsonPrimitive?.int?.seconds,
            clock = clock,
            jitter = TestJitter(backoff = backoff, errorTtl = 3.seconds),
        )

        // The access key ID of the credentials currently cached, if any.
        var cachedKey: String? = null
        val state = given.string("cachedCredentials")
        if (state != "none") {
            val seedKey = given["accessKeyId"]?.jsonPrimitive?.content ?: "AKID-CACHED"
            source.next = { source.credentials(seedKey, seedLifetime) }
            provider.resolve()
            cachedKey = seedKey
            clock.advance(
                when (state) {
                    "valid" -> Duration.ZERO
                    "advisory" -> 61.minutes
                    "mandatory" -> seedLifetime - 30.seconds
                    "expired" -> seedLifetime + 1.minutes
                    else -> fail("[$id] unknown cachedCredentials state `$state`")
                },
            )
        }

        case["steps"]!!.jsonArray.forEachIndexed { index, element ->
            val step = element.jsonObject
            val where = "[$id] step $index"
            when (val type = step.string("type")) {
                "advanceTime" -> clock.advance(step["seconds"]!!.jsonPrimitive.int.seconds)

                "invalidate" -> provider.invalidate(
                    Credentials(accessKeyId = step.string("rejectedAccessKeyId"), secretAccessKey = "secret"),
                )

                "getCredentials" -> {
                    val expected = step["expected"]!!.jsonObject
                    val freshKey = "AKID-FRESH-$id-$index"
                    source.next = when (step["response"]?.jsonPrimitive?.content) {
                        null -> null
                        "freshCredentials" -> {
                            val lifetime = step["lifetimeSeconds"]?.jsonPrimitive?.int?.seconds ?: defaultFreshLifetime
                            ({ source.credentials(freshKey, lifetime) })
                        }
                        "staleCredentials" -> ({ source.credentials("AKID-STALE-$id-$index", (-1).minutes) })
                        "error" -> ({ throw ClientException("credential source unavailable") })
                        "nonRecoverableError" -> ({ throw nonRecoverableError() })
                        else -> fail("$where: unknown response")
                    }

                    val entryBefore = provider.cachedEntry
                    val rateLimited = entryBefore?.nextRefreshAllowedAt?.let { clock.now() < it } ?: false
                    val callsBefore = source.calls

                    val outcome = runCatching { provider.resolve() }

                    assertEquals(false, source.unexpectedCall, "$where: the source was contacted unexpectedly")
                    assertEquals(expected.bool("sourceContacted"), source.calls > callsBefore, "$where: sourceContacted")
                    assertEquals(expected.bool("rateLimited"), rateLimited, "$where: rateLimited")

                    when (val result = expected.string("result")) {
                        "newCredentials" -> {
                            assertEquals(freshKey, outcome.getOrNull()?.accessKeyId, "$where: expected new credentials, got $outcome")
                            cachedKey = freshKey
                        }
                        "cachedCredentials" -> assertEquals(
                            cachedKey,
                            outcome.getOrNull()?.accessKeyId,
                            "$where: expected the cached credentials, got $outcome",
                        )
                        "noCredentialsError" -> {
                            val ex = outcome.exceptionOrNull() ?: fail("$where: expected an error, got $outcome")
                            assertEquals(false, ex.isNonRecoverableCredentialsError(), "$where: expected a recoverable error")
                        }
                        "nonRecoverableError" -> {
                            val ex = outcome.exceptionOrNull() ?: fail("$where: expected an error, got $outcome")
                            assertEquals(true, ex.isNonRecoverableCredentialsError(), "$where: expected a non-recoverable error")
                        }
                        else -> fail("$where: unknown result `$result`")
                    }

                    expected["advisoryWindowSeconds"]?.jsonPrimitive?.int?.let { window ->
                        val entry = provider.cachedEntry ?: fail("$where: nothing cached")
                        val actual = entry.expiresAt!! - entry.advisoryAt
                        assertEquals(window.seconds, actual, "$where: advisoryWindowSeconds")
                    }
                }

                else -> fail("$where: unknown step type `$type`")
            }
        }
    }

    private fun nonRecoverableError() = ClientException("needs customer action").apply {
        sdkErrorMetadata.attributes[ErrorMetadata.NonRecoverable] = true
    }

    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.bool(key: String) = this[key]!!.jsonPrimitive.boolean
}
