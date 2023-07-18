/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A value that is produced asynchronously and cached after first initialized.
 *
 * Similar to `Lazy<T>` but supports asynchronous initialization. Implementations
 * MUST be thread safe.
 *
 * NOTE: Properties cannot be loaded asynchronously so unlike `Lazy<T>` a `LazyAsyncValue<T>` cannot be a property
 * delegate.
 */
@InternalApi
public interface LazyAsyncValue<out T> {
    /**
     * Get the cached value or initialize it for the first time. Subsequent calls will return the same value.
     */
    public suspend fun get(): T
}

/**
 * Create a [LazyAsyncValue] with the given [initializer]
 */
@InternalApi
public fun <T> asyncLazy(initializer: suspend () -> T): LazyAsyncValue<T> = LazyAsyncValueImpl(initializer)

internal object UNINITIALIZED_VALUE

/**
 * A value that is initialized asynchronously and cached after it is initialized. Loading/access is thread safe.
 */
private class LazyAsyncValueImpl<out T> (initializer: suspend () -> T) : LazyAsyncValue<T> {
    private val mu = Mutex()
    private var initializer: (suspend () -> T)? = initializer
    private var value: Any? = UNINITIALIZED_VALUE

    override suspend fun get(): T = mu.withLock {
        if (value === UNINITIALIZED_VALUE) {
            value = initializer!!()
            initializer = null
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
