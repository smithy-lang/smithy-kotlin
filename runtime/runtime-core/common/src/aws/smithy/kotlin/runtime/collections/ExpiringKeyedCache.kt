/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.collections

import aws.smithy.kotlin.runtime.util.ExpiringValue

/**
 * A multi-value cache which supports retrieval and invalidation via a key paired with each value. The [get] and
 * [invalidate] methods are `suspend` functions to allow for cross-context synchronization and potentially-expensive
 * value lookup.
 *
 * Values in the cache _may_ expire and are retrieved as [ExpiringValue]. When a value is absent/expired in the cache,
 * invoking [get] will cause a lookup to occur via the function's `valueLookup` parameter.
 *
 * @param K The type of the keys of this cache
 * @param V The type of the values of this cache
 */
public interface ExpiringKeyedCache<K, V> {
    /**
     * The number of values currently stored in the cache
     */
    public val size: Int

    /**
     * Gets the value associated with this key from the cache. If the cache does not contain the given key,
     * implementations are expected to invoke [valueLookup], although they _may_ perform other actions such as throw
     * exceptions, fall back to other caches, etc.
     * @param key The key for which to look up a value
     * @param valueLookup A possibly-suspending function which returns the read-through value associated with a given
     * key. This function is invoked when the cache does not contain the given [key] or when the value is expired.
     */
    public suspend fun get(key: K, valueLookup: suspend (K) -> ExpiringValue<V>): V

    /**
     * Invalidates the value (if any) for the given key, removing it from the cache regardless. This method has no
     * effect if the given key is not present in the cache.
     * @param key The key for which to invalidate a value
     */
    public suspend fun invalidate(key: K)
}
