/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.atomicfu.atomic

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
 * A [Closeable] resource that may be shared by multiple consumers.
 *
 * A user invokes the [share] method to indicate that they're using the resource, and calls [close] to release like with
 * a standard [Closeable], but the entity is only truly closed once the final user has released it.
 */
public interface SharedCloseable : Closeable {
    public fun share()
}

/**
 * Wraps a [Closeable], implementing share count logic to expose the delegate as a [SharedCloseable].
 */
@InternalApi
public class SharedCloseableImpl<T : Closeable>(private val delegate: T) : SharedCloseable {
    private val shareCount = atomic(0)

    public override fun share() {
        shareCount.incrementAndGet()
    }

    public override fun close() {
        if (shareCount.decrementAndGet() <= 0) {
            delegate.close()
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
