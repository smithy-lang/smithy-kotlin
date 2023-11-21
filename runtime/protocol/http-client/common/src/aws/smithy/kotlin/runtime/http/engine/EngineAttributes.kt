/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.AttributeKey
import kotlin.time.Duration

/**
 * Common attributes related to HTTP engines.
 */
@InternalApi
public object EngineAttributes {
    /**
     * The time between sending the request completely and receiving the first byte of the response. This effectively
     * measures the time spent waiting on a response.
     */
    public val TimeToFirstByte: AttributeKey<Duration> = AttributeKey("aws.smithy.kotlin#TimeToFirstByte")
}
