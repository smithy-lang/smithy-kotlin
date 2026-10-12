/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.atomicfu.atomic
import kotlin.time.Duration

/**
 * Holds the most recent non-recoverable credential resolution failure for a short window and rethrows it.
 *
 * Its own type because it shares no state with the refresh windows: its own deadline, its own atomic, and its own
 * reason to change. Its purpose is to keep a burst of concurrent requests from each making the same doomed call to a
 * source that has already reported a misconfiguration.
 */
internal class NonRecoverableErrorCache(private val clock: Clock) {
    private data class Entry(val error: Throwable, val expiresAt: Instant)

    private val cached = atomic<Entry?>(null)

    /** Rethrows the cached error if one is still live, expiring it in passing if it is not. */
    fun throwIfLive() {
        val entry = cached.value ?: return
        if (clock.now() >= entry.expiresAt) {
            // compareAndSet rather than a plain write so a newer error arriving concurrently is not erased.
            cached.compareAndSet(entry, null)
            return
        }
        throw entry.error
    }

    fun record(error: Throwable, ttl: Duration) {
        cached.value = Entry(error, clock.now() + ttl)
    }

    fun clear() {
        cached.value = null
    }
}
