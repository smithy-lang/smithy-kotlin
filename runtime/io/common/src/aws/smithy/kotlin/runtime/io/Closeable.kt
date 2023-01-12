/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

// this really should live in the stdlib...
// https://youtrack.jetbrains.com/issue/KT-31066

/**
 * A resource than can be closed. The [close] method is invoked to release resources
 * an object is holding (e.g. such as open files)
 */
public expect interface Closeable {
    /**
     * Release any resources held by this object
     */
    public fun close()
}

/**
 * Implements share count logic to track usage of internally created [Closeable]s.
 *
 * A user invokes the [share] method to indicate that they're using the resource, and calls [close] to release like with
 * a standard [Closeable], but the entity is only truly closed once the final user has released it.
 */
@InternalApi
public open class ManagedCloseable(private val closeable: Closeable) : Closeable {
    private val state = object : SynchronizedObject() {
        var shareCount: Int = 0
        var isClosed: Boolean = false
    }

    public fun share() {
        synchronized(state) {
            if (state.isClosed) {
                throw IllegalStateException("caller attempted to share() a closed object")
            }
            state.shareCount++
        }
    }

    public override fun close() {
        synchronized(state) {
            if (state.isClosed) return@synchronized

            state.shareCount--
            if (state.shareCount > 0) return@synchronized

            state.isClosed = true
            closeable.close()
        }
    }
}

/**
 * Executes the given [block] on this resource and then closes it whether an exception has
 * occurred or not.
 */
public inline fun <C : Closeable, R> C.use(block: (C) -> R): R {
    var closed = false

    return try {
        block(this)
    } catch (first: Throwable) {
        try {
            closed = true
            close()
        } catch (second: Throwable) {
            first.addSuppressed(second)
        }

        throw first
    } finally {
        if (!closed) {
            close()
        }
    }
}
