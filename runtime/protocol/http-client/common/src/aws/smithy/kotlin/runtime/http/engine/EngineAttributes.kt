/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.AttributeKey
import kotlin.time.Duration

@InternalApi
public object EngineAttributes {
    public val TimeToFirstByte: AttributeKey<Duration> = AttributeKey("TimeToFirstByte")
}
