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
 * rejection arriving while a refresh is in flight must not be dropped. Behind three named methods that rule is
 * reviewable; as a bare atomic and a comment in the middle of the resolve path it was not.
 *
 * Reading a rejection does not clear it. It stays recorded until a refresh installs new credentials, so every caller
 * that reads the cache in the meantime sees the mandatory state, and waits for the refresh instead of being handed
 * the rejected credentials. Matching by access key ID rather than by object identity means a rejection that names
 * credentials which have already been replaced matches nothing, so it forces no pointless refresh.
 */
internal class InvalidationSignal {
    private val rejectedAccessKeyId = atomic<String?>(null)

    /**
     * Records a rejection: one unconditional write, with no reference to what is currently cached.
     *
     * The comparison happens on the resolve path, which is the only place that can make it against a value that is
     * not already moving.
     */
    fun record(rejected: Credentials) {
        rejectedAccessKeyId.value = rejected.accessKeyId
    }

    /** Whether the last recorded rejection names [held]. Does not clear it. */
    fun isRejected(held: Credentials): Boolean = rejectedAccessKeyId.value == held.accessKeyId

    fun clear() {
        rejectedAccessKeyId.value = null
    }
}
