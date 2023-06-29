/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client

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
         * Configure the `sdk.logMode` JVM system property, `SDK_LOG_MODE` environment variable,
         * or use this setting to opt in to additional debug logging.
         *
         * This can be used to configure logging of requests, responses, retries, etc of SDK clients.
         *
         * **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
         * performance considerations when dumping the request/response body. This is primarily a tool for
         * debug purposes.
         */
        public var logMode: LogMode?
    }
}
