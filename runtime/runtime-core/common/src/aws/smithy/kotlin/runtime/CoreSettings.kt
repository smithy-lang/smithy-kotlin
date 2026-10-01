/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.config.boolEnvSetting
import aws.smithy.kotlin.runtime.config.resolve
import aws.smithy.kotlin.runtime.util.PlatformEnvironProvider
import aws.smithy.kotlin.runtime.util.PlatformProvider

@InternalApi
public object CoreSettings {
    private val newRetriesSetting = boolEnvSetting("smithy.newRetries2026", "SMITHY_NEW_RETRIES_2026").orElse(false)

    /**
     * Resolve whether the new standard retry strategy behavior is enabled.
     * Controlled by the `smithy.newRetries2026` system property or `SMITHY_NEW_RETRIES_2026` environment variable.
     * @param platform The [PlatformEnvironProvider] to use. Defaults to [PlatformProvider.System].
     */
    public fun resolveNewRetriesEnabled(platform: PlatformEnvironProvider = PlatformProvider.System): Boolean = newRetriesSetting.resolve(platform) ?: false
}
