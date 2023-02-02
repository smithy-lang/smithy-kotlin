/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
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
     * Invoked when a caller releases the resource. Returns a boolean indicating whether the resource has been fully
     * unshared with this call (i.e. the caller was the only remaining user). Future calls on a fully unshared object
     * would return false.
     */
    public fun unshare(): Boolean
}

/**
 * Abstract class which implements usage count tracking for [SdkManaged].
 */
@InternalApi
public abstract class SdkManagedBase : SdkManaged {
    private val state = object : SynchronizedObject() {
        var shareCount: Int = 0
        var isUnshared: Boolean = false
    }

    override fun share() {
        synchronized(state) {
            check(!state.isUnshared) { "caller attempted to share() a fully unshared object" }

            state.shareCount++
        }
    }

    override fun unshare(): Boolean = synchronized(state) {
        if (state.isUnshared) return false

        state.shareCount--
        if (state.shareCount > 0) return false

        state.isUnshared = true
        true
    }
}
