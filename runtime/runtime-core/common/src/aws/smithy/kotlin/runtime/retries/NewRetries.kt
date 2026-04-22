/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.config.boolEnvSetting
import aws.smithy.kotlin.runtime.config.resolve

private val NEW_RETRIES_SETTING = boolEnvSetting("smithy.newRetries2026", "SMITHY_NEW_RETRIES_2026").orElse(false)

/**
 * Returns `true` when the `SMITHY_NEW_RETRIES_2026` environment variable (or `smithy.newRetries2026` system property)
 * is set to `true`, enabling SEP 2.1 retry behavior. Defaults to `false`.
 */
@InternalApi
public fun newRetriesEnabled(): Boolean = NEW_RETRIES_SETTING.resolve() ?: false
