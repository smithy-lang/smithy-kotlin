/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.config.boolEnvSetting
import aws.smithy.kotlin.runtime.config.resolve

@InternalApi
public object CoreSettings {
    private val newRetriesSetting = boolEnvSetting("smithy.newRetries2026", "SMITHY_NEW_RETRIES_2026").orElse(false)

    /**
     * Whether the standard retry strategy behavior is enabled.
     * Controlled by the `SMITHY_NEW_RETRIES_2026` environment variable or `smithy.newRetries2026` system property.
     *
     * Uses a computed property (`get()`) instead of a stored `val` so the value is re-evaluated on each access,
     * allowing tests to toggle the system property between test methods within the same JVM.
     */
    public val NewRetriesEnabled: Boolean
        get() = newRetriesSetting.resolve() ?: false
}
