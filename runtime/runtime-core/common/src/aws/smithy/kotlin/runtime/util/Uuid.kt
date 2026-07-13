/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import kotlin.uuid.Uuid as KotlinUuid

/**
 * Internal UUID representation using raw high/low longs for wire-format serialization (e.g., event-stream headers).
 * Random generation delegates to [kotlin.uuid.Uuid] for platform-appropriate secure randomness.
 */
@Deprecated("Use kotlin.uuid.Uuid directly", ReplaceWith("kotlin.uuid.Uuid"))
@InternalApi
public data class Uuid(val high: Long, val low: Long) {
    @InternalApi
    public companion object {
        @Suppress("DEPRECATION")
        public fun random(): Uuid =
            KotlinUuid.random().toLongs { high, low -> Uuid(high, low) }
    }

    override fun toString(): String = KotlinUuid.fromLongs(high, low).toString()
}
