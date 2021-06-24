/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.config

import aws.smithy.kotlin.runtime.client.SdkLogMode

/**
 * Common configuration options for any generated SDK client
 */
interface SdkClientConfig {
    /**
     * Configure events that will be logged. By default clients will not output
     * raw requests or responses. Use this setting to opt-in to additional debug logging.
     *
     * This can be used to configure logging of requests, responses, retries, etc of SDK clients.
     *
     * **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
     * performance considerations when dumping the request/response body. This is primarily a tool for
     * debug purposes.
     */
    val sdkLogMode: SdkLogMode
        get() = SdkLogMode.None
}
