/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import kotlinx.atomicfu.atomic

/**
 * Records that a target service rejected one specific set of credentials, identified by access key ID.
 *
 * Its own type because it is the one piece of cache state deliberately maintained *outside* the refresh lock: a
 * rejection arriving while a refresh is in flight must not be dropped. Behind four named methods that rule is
 * reviewable; as a bare atomic and a comment in the middle of the resolve path it was not.
 *
 * Matching by access key ID rather than by object identity means a rejection that names credentials which have
 * already been replaced is discarded rather than forcing a pointless refresh.
 */
internal class InvalidationSignal {
    private val rejectedAccessKeyId = atomic<String?>(null)

    /**
     * Records a rejection: one unconditional write, with no reference to what is currently cached.
     *
     * The comparison happens later, on the resolve path, which is the only place that can make it against a value
     * that is not already moving.
     */
    fun record(rejected: Credentials) {
        rejectedAccessKeyId.value = rejected.accessKeyId
    }

    /**
     * Reads and clears any pending rejection, reporting whether it named [held].
     *
     * Cleared either way: a match has been acted on, and a non-match names credentials that no longer exist.
     * `compareAndSet` rather than a plain write so a newer rejection arriving concurrently is not erased.
     */
    fun consumeIfMatches(held: Credentials): Boolean {
        val rejected = rejectedAccessKeyId.value ?: return false
        rejectedAccessKeyId.compareAndSet(rejected, null)
        return held.accessKeyId == rejected
    }

    /** Re-arms a rejection that was consumed but could not be acted on because the refresh backoff was still live. */
    fun rearm(held: Credentials) {
        rejectedAccessKeyId.value = held.accessKeyId
    }

    fun clear() {
        rejectedAccessKeyId.value = null
    }
}
