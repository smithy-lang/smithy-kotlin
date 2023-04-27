/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client.config

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.LogMode
import aws.smithy.kotlin.runtime.config.EnvironmentSetting
import aws.smithy.kotlin.runtime.config.enumEnvSetting
import aws.smithy.kotlin.runtime.config.intEnvSetting
import aws.smithy.kotlin.runtime.config.strEnvSetting

@InternalApi
public object ClientSettings {
    /**
     * The maximum number of request attempts to perform. This is one more than the number of retries, so
     * maxAttempts = 1 will have 0 retries.
     */
    public val MaxAttempts: EnvironmentSetting<Int> = intEnvSetting("sdk.maxAttempts", "SDK_MAX_ATTEMPTS")

    /**
     * Which RetryMode to use for the default RetryPolicy, when one is not specified at the client level.
     */
    public val RetryMode: EnvironmentSetting<RetryMode> = enumEnvSetting<RetryMode>("sdk.retryMode", "SDK_RETRY_MODE")

    public val LogMode: EnvironmentSetting<LogMode> = EnvironmentSetting(
        aws.smithy.kotlin.runtime.client.LogMode::fromString, "sdk.logMode", "SDK_LOG_MODE"
    )
}
