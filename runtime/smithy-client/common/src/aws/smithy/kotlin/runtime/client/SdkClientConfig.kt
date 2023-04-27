/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.retries.RetryStrategy
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import aws.smithy.kotlin.runtime.util.Buildable

/**
 * Common configuration options for any generated SDK client
 */
public interface SdkClientConfig {
    /**
     * A reader-friendly name for the client.
     */
    public val clientName: String

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
    public val logMode: LogMode
        get() = LogMode.Default

    /**
     * The policy to use for evaluating operation results and determining whether/how to retry.
     */
    public val retryPolicy: RetryPolicy<Any?>

    /**
     * The [RetryStrategy] the client will use to retry failed operations.
     */
    public val retryStrategy: RetryStrategy

    /**
     * Configurable properties that all client configuration exposes.
     *
     * @param TConfig the type of configuration built by this builder
     */
    public interface Builder<TConfig : SdkClientConfig> : Buildable<TConfig> {
        /**
         * A reader-friendly name for the client.
         */
        public var clientName: String

        /**
         * Configure events that will be logged. By default, clients will not output
         * raw requests or responses.
         * Configure the `AWS_SDK_KOTLIN_LOG_MODE` environment variable,
         * `aws.sdk.kotlin.logMode` JVM system property, or use this setting to opt in to additional debug logging.
         *
         * This can be used to configure logging of requests, responses, retries, etc of SDK clients.
         *
         * **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
         * performance considerations when dumping the request/response body. This is primarily a tool for
         * debug purposes.
         */
        public var logMode: LogMode?

        /**
         * The policy to use for evaluating operation results and determining whether/how to retry.
         */
        public var retryPolicy: RetryPolicy<Any?>?

        /**
         * Configure the [RetryStrategy] the client will use to retry failed operations.
         */
        public var retryStrategy: RetryStrategy?
    }
}
