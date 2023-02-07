/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi

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

@InternalApi
public fun Any.closeIfCloseable() {
    if (this is Closeable) close()
}
