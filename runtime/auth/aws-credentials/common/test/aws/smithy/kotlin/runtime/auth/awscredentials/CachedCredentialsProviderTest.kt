/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.ManualClock
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CachedCredentialsProviderTest {
    private val epoch = Instant.fromIso8601("2020-10-16T03:56:00Z")
    private val testExpiration = epoch + 30.minutes
    private val testClock = ManualClock(epoch)

    private class TestCredentialsProvider(
        private val expiration: Instant? = null,
    ) : CredentialsProvider {
        var callCount = 0

        override suspend fun resolve(attributes: Attributes): Credentials {
            callCount++
            return Credentials(
                "AKID",
                "secret",
                expiration = this@TestCredentialsProvider.expiration,
            )
        }
    }

    @Test
    fun testLoadFirstCall() = runTest {
        // explicit expiration
        val source = TestCredentialsProvider(expiration = testExpiration)
        val provider = CachedCredentialsProvider(source, clock = testClock)
        val creds = provider.resolve()
        val expected = Credentials("AKID", "secret", expiration = testExpiration)
        assertEquals(expected, creds)
        assertEquals(1, source.callCount)

        provider.resolve()
        assertEquals(1, source.callCount)
    }

    @Test
    fun testDefaultExpiration() = runTest {
        // expiration should come from the cached provider
        val source = TestCredentialsProvider()
        val provider = CachedCredentialsProvider(source, clock = testClock)
        val creds = provider.resolve()
        val expectedExpiration = epoch + 15.minutes
        val expected = Credentials("AKID", "secret", expiration = expectedExpiration)
        assertEquals(expected, creds)
        assertEquals(1, source.callCount)
    }

    @Test
    fun testReloadExpiredCredentials() = runTest {
        val source = TestCredentialsProvider(expiration = testExpiration)
        val provider = CachedCredentialsProvider(source, clock = testClock)
        val creds = provider.resolve()
        val expected = Credentials("AKID", "secret", expiration = testExpiration)
        assertEquals(expected, creds)
        assertEquals(1, source.callCount)

        // 1 min past expiration
        testClock.advance(31.minutes)
        provider.resolve()
        assertEquals(2, source.callCount)
    }

    @Test
    fun testRefreshBufferWindow() = runTest {
        val source = TestCredentialsProvider(expiration = testExpiration)
        val provider = CachedCredentialsProvider(source, clock = testClock, expireCredentialsAfter = 60.minutes)
        val creds = provider.resolve()
        val expected = Credentials("AKID", "secret", expiration = testExpiration)
        assertEquals(expected, creds)
        assertEquals(1, source.callCount)

        // default buffer window is 10 seconds, advance 29 minutes, 49 seconds
        testClock.advance((29 * 60 + 49).seconds)
        provider.resolve()
        // not within window yet
        assertEquals(1, source.callCount)

        // now we should be within 10 sec window
        testClock.advance(1.seconds)
        provider.resolve()
        assertEquals(2, source.callCount)
    }

    @Test
    fun testLoadFailed() = runTest {
        val source = object : CredentialsProvider {
            private var count = 0
            override suspend fun resolve(attributes: Attributes): Credentials {
                if (count <= 0) {
                    count++
                    throw RuntimeException("test error")
                }
                return Credentials("AKID", "secret")
            }
        }
        val provider = CachedCredentialsProvider(source, clock = testClock)

        assertFailsWith<RuntimeException> {
            provider.resolve()
        }.message.shouldContain("test error")

        // future successful invocations should continue to work
        provider.resolve()
    }

    @Test
    fun testItThrowsOnGetCredentialsAfterClose() = runTest {
        val source = TestCredentialsProvider(expiration = testExpiration)
        val provider = CachedCredentialsProvider(source, clock = testClock)
        val creds = provider.resolve()
        val expected = Credentials("AKID", "secret", expiration = testExpiration)
        assertEquals(expected, creds)
        assertEquals(1, source.callCount)

        provider.close()

        assertFailsWith<IllegalStateException> {
            provider.resolve()
        }
        assertEquals(1, source.callCount)
    }

    @Test
    fun testCachedConvenienceFunction() = runTest {
        val source = TestCredentialsProvider(expiration = testExpiration)
        val provider = source.cached(clock = testClock)

        val creds = provider.resolve()
        val expected = Credentials("AKID", "secret", expiration = testExpiration)
        assertEquals(expected, creds)
        assertEquals(1, source.callCount)

        provider.resolve()
        assertEquals(1, source.callCount)
    }
}
