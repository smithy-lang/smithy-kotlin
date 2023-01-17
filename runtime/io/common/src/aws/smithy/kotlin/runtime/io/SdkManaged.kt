/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Interface that the lifecycle of some resource is managed by the SDK at runtime.
 */
@InternalApi
public interface SdkManaged {
    /**
     * Invoked by a caller to declare usership of the resource.
     */
    public fun share()

    /**
     * Invoked when a caller releases the resource. Returns a boolean indicating whether the resource has been released
     * with this call (i.e. no other callers are sharing it). Future calls on an already-released object would return
     * false.
     */
    public fun unshare(): Boolean
}

/**
 * Abstract class which implements usage count tracking for [SdkManaged].
 */
@InternalApi
public abstract class SdkManagedImpl : SdkManaged {
    private val state = object : SynchronizedObject() {
        var shareCount: Int = 0
        var isReleased: Boolean = false
    }

    override fun share() {
        synchronized(state) {
            check(!state.isReleased) { "caller attempted to share() a released object" }

            state.shareCount++
        }
    }

    override fun unshare(): Boolean = synchronized(state) {
        if (state.isReleased) return false

        state.shareCount--
        if (state.shareCount > 0) return false

        state.isReleased = true
        true
    }
}
