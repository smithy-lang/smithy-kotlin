/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.util.PlatformEnvironProvider
import aws.smithy.kotlin.runtime.util.PlatformProvider

@InternalApi
@Deprecated("The new retry behavior is always enabled. This object will be removed in a future release.")
@PlannedRemoval(1, 9)
public object CoreSettings {
    /**
     * Always returns `true`. The new retry behavior is no longer gated by the `smithy.newRetries2026` system property
     * or the `SMITHY_NEW_RETRIES_2026` environment variable.
     * @param platform Ignored
     */
    @Deprecated("The new retry behavior is always enabled. This function will be removed in a future release.")
    @PlannedRemoval(1, 9)
    public fun resolveNewRetriesEnabled(
        @Suppress("UNUSED_PARAMETER") platform: PlatformEnvironProvider = PlatformProvider.System,
    ): Boolean = true
}
