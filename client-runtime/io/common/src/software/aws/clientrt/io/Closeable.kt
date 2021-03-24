/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.io

// this really should live in the stdlib...
// https://youtrack.jetbrains.com/issue/KT-31066

public expect interface Closeable {
    public fun close()
}

public inline fun <C : Closeable, R> C.use(block: (C) -> R): R {
    var closed = false

    return try {
        block(this)
    } catch (first: Throwable) {
        try {
            closed = true
            close()
        } catch (second: Throwable) {
            first.addSuppressedInternal(second)
        }

        throw first
    } finally {
        if (!closed) {
            close()
        }
    }
}

@PublishedApi
internal expect fun Throwable.addSuppressedInternal(other: Throwable)
