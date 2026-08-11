/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.schema.trait.TimestampFormat

public data class JsonCodecSettings(
    public val useJsonName: Boolean = false,
    public val defaultTimestampFormat: TimestampFormat = TimestampFormat.EPOCH_SECONDS,
)
