/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.retries.RetryStrategy
import aws.smithy.kotlin.runtime.util.Buildable

/**
 * Common configuration options for any generated SDK client
 */
public interface SdkClientConfig {
    /**
     * Configure events that will be logged. By default, clients will not output
     * raw requests or responses. Use this setting to opt in to additional debug logging.
     *
     * This can be used to configure logging of requests, responses, retries, etc of SDK clients.
     *
     * **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
     * performance considerations when dumping the request/response body. This is primarily a tool for
     * debug purposes.
     */
    public val sdkLogMode: SdkLogMode
        get() = SdkLogMode.Default

    // FIXME - should we be taking RetryPolicy as well?
    /**
     * The [RetryStrategy] the client will use to retry failed operations.
     */
    public val retryStrategy: RetryStrategy

    // FIXME - do we need an interceptor config builder? can we do that here with protocol specific differences

    public interface Builder<TConfig : SdkClientConfig> : Buildable<TConfig> {
        /**
         * Configure events that will be logged. See [SdkClientConfig.sdkLogMode].
         */
        public var sdkLogMode: SdkLogMode

        /**
         * Configure the [RetryStrategy] the client will use to retry failed operations.
         */
        public var retryStrategy: RetryStrategy?
    }
}
