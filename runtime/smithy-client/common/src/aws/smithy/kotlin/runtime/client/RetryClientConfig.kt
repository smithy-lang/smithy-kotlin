/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.retries.RetryStrategy
import aws.smithy.kotlin.runtime.retries.RetryStrategyConfigDsl
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import aws.smithy.kotlin.runtime.util.DslBuilderProperty
import aws.smithy.kotlin.runtime.util.DslFactory

public interface RetryClientConfig : RetryStrategyClientConfig {
    /**
     * The policy to use for evaluating operation results and determining whether/how to retry.
     */
    public val retryPolicy: RetryPolicy<Any?>

    public interface Builder {
        /**
         * The policy to use for evaluating operation results and determining whether/how to retry.
         */
        public var retryPolicy: RetryPolicy<Any?>?
    }
}

public interface RetryStrategyClientConfig {
    /**
     * The [RetryStrategy] the client will use to retry failed operations.
     */
    public val retryStrategy: RetryStrategy

    @RetryStrategyConfigDsl
    public interface Builder {
        public fun retryStrategy(block: StandardRetryStrategy.Config.Builder.() -> Unit)

        public fun <B : RetryStrategy.Config.Builder, R : RetryStrategy> retryStrategy(
            factory: DslFactory<B, R>,
            block: B.() -> Unit,
        )

        /**
         * The [RetryStrategy] the client will use to retry failed operations.
         */
        public var retryStrategy: RetryStrategy?

        @InternalApi
        public fun buildRetryStrategyClientConfig(): RetryStrategyClientConfig
    }
}

@InternalApi
public class RetryStrategyClientConfigImpl private constructor(
    override val retryStrategy: RetryStrategy,
) : RetryStrategyClientConfig {
    @InternalApi
    public class BuilderImpl : RetryStrategyClientConfig.Builder {
        private val retryStrategyProperty = DslBuilderProperty<RetryStrategy.Config.Builder, RetryStrategy>(
            StandardRetryStrategy,
            { config.toBuilderApplicator() },
        )

        override var retryStrategy: RetryStrategy? by retryStrategyProperty::instance

        override fun retryStrategy(block: StandardRetryStrategy.Config.Builder.() -> Unit) {
            retryStrategyProperty.dsl(StandardRetryStrategy, block)
        }

        override fun <B : RetryStrategy.Config.Builder, R : RetryStrategy> retryStrategy(
            factory: DslFactory<B, R>,
            block: B.() -> Unit,
        ) {
            retryStrategyProperty.dsl(factory, block)
        }

        @InternalApi
        override fun buildRetryStrategyClientConfig(): RetryStrategyClientConfig =
            RetryStrategyClientConfigImpl(retryStrategyProperty.supply())
    }
}
