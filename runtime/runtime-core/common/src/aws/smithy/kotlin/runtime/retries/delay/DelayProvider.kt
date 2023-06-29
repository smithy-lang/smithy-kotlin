/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.delay

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.retries.RetryStrategyConfigDsl

/**
 * An object that can be used to delay between iterations of code.
 */
public interface DelayProvider {
    public val config: Config

    /**
     * Delays for an appropriate amount of time after the given attempt number.
     * @param attempt The ordinal index of the attempt, used in calculating the exact amount of time to delay.
     */
    public suspend fun backoff(attempt: Int)

    /**
     * Configuration for a delay provider
     */
    public interface Config {
        @InternalApi
        public fun toBuilderApplicator(): Builder.() -> Unit

        /**
         * A builder for configs for delay providers
         */
        @RetryStrategyConfigDsl
        public interface Builder
    }
}
