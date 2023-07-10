/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * An object which caches values and allows retrieving them by key. The values expire after a time. If a value is
 * expired or absent from the cache, it will be read from [valueLookup] and then cached.
 *
 * A sweep operation will run prior to a read that happens after [minimumSweepPeriod] has elapsed from the last sweep
 * (or the initialization of the cache). This sweep will search for and remove expired entries from the cache.
 *
 * Accesses to this cache are thread-safe via a mutex. All [get] and sweep operations will wait for prior invocations to
 * complete. Note that the [size] property is non-volatile and so may return stale information.
 *
 * @param K The type of the keys of this cache
 * @param V The type of the values of this cache
 * @param minimumSweepPeriod The minimum time between sweeps. Sweeps may occur after longer durations if no reads occur
 * after [minimumSweepPeriod] has elapsed. Defaults to 10 minutes.
 * @param clock The [Clock] to use for measuring time. Defaults to [Clock.System].
 * @param valueLookup A possibly-suspending function which returns the read-through value associated with a given key.
 * This function is invoked when the cache, for a given key, does not contain a value or the value is expired.
 */
@InternalApi
public class ReadThroughCache<K, V>(
    private val minimumSweepPeriod: Duration,
    private val clock: Clock = Clock.System,
    private val valueLookup: suspend (K) -> ExpiringValue<V>,
) {
    private val map = mutableMapOf<K, ExpiringValue<V>>()
    private val mutex = Mutex()
    private var nextSweep = clock.now() + minimumSweepPeriod

    /**
     * Gets the value associated with this key from the cache. If the cache does not contain the value or the value is
     * expired, it will be read-through from the [valueLookup] function.
     * @param key The key for which to look up a value.
     * @param value The cached value.
     */
    public suspend fun get(key: K): V = mutex.withLock {
        if (clock.now() > nextSweep) sweep()

        val current = map[key]
        return if (current == null || current.isExpired) {
            valueLookup(key).also { map[key] = it }.value
        } else {
            current.value
        }
    }

    private val ExpiringValue<*>.isExpired: Boolean
        get() = clock.now() >= expiresAt

    /**
     * Gets the number of values currently stored in the cache.
     */
    public val size: Int
        get() = map.size

    private fun sweep() {
        val iterator = map.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.isExpired) {
                iterator.remove()
            }
        }
        nextSweep = clock.now() + minimumSweepPeriod
    }
}
