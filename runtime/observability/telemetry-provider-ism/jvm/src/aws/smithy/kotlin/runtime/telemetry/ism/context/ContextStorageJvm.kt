/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.ism.context

import aws.smithy.kotlin.runtime.telemetry.context.Context
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update

internal actual fun ContextStorage(initialContext: Context): ContextStorage = ThreadLocalContextStorage(initialContext)

private class ThreadLocalContextStorage(private val initialContext: Context) : ContextStorage {
    private val threadLocal = ThreadLocal.withInitial { AtomicHolder(initialContext) }

    override fun get(): Context = threadLocal.get().get()
    override fun getAndSet(value: Context): Context = threadLocal.get().getAndSet(value)
    override fun requireAndSet(expect: Context, update: Context) = threadLocal.get().requireAndSet(expect, update)
}

private class AtomicHolder(initialContext: Context) : ContextStorage {
    private val current = atomic(initialContext)

    override fun get(): Context = current.value

    override fun getAndSet(value: Context): Context = current.getAndSet(value)

    override fun requireAndSet(expect: Context, update: Context) = current.update { existing ->
        check(existing == expect) { "Invalid state when updating context! Expected = $expect, actual = $existing" }
        update
    }
}
