/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

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
interface LazyAsyncValue<out T> {
    /**
     * Get the cached value or initialize it for the first time. Subsequent calls will return the same value.
     */
    suspend fun get(): T
}

/**
 * Create a [LazyAsyncValue] with the given [initializer]
 */
public fun <T> asyncLazy(initializer: suspend () -> T): LazyAsyncValue<T> = LazyAsyncValueImpl(initializer)

/**
 * A value that is initialized asynchronously and cached after it is initialized. Loading/access is thread safe.
 */
private class LazyAsyncValueImpl<T> (initializer: suspend () -> T) : LazyAsyncValue<T> {
    private val mu = Mutex()
    private var initializer: (suspend () -> T)? = initializer
    private var value: T? = null

    override suspend fun get(): T = mu.withLock {
        if (value != null) return value!!
        value = initializer!!()
        initializer = null
        return value!!
    }
}
