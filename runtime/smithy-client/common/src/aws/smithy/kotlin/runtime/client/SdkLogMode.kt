/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

/**
 * SdkLogMode represents the logging mode of SDK clients. The mode is backed by a bit-field where each
 * bit is a flag (mode) that describes the logging behavior for one or more client components.
 *
 * Example: Setting log mode to enable logging of requests and responses
 * ```
 * val mode = LogMode.LogRequest + LogMode.LogResponse
 * ```
 */
public sealed class SdkLogMode(private val mask: Int) {
    /**
     * The default logging mode which does not opt-in to anything
     */
    public object Default : SdkLogMode(0x00) {
        override fun toString(): String = "Default"
    }

    /**
     * Log the request details, e.g. url, headers, etc.
     */
    public object LogRequest : SdkLogMode(0x01) {
        override fun toString(): String = "LogRequest"
    }

    /**
     * Log the request details as well as the body if possible
     */
    public object LogRequestWithBody : SdkLogMode(0x02) {
        override fun toString(): String = "LogRequestWithBody"
    }

    /**
     * Log the response details, e.g. status, headers, etc
     */
    public object LogResponse : SdkLogMode(0x04) {
        override fun toString(): String = "LogResponse"
    }

    /**
     * Log the response details as well as the body if possible
     */
    public object LogResponseWithBody : SdkLogMode(0x08) {
        override fun toString(): String = "LogResponseWithBody"
    }

    internal class Composite(mask: Int) : SdkLogMode(mask)

    public operator fun plus(mode: SdkLogMode): SdkLogMode = Composite(mask or mode.mask)
    public operator fun minus(mode: SdkLogMode): SdkLogMode = Composite(mask and mode.mask.inv())

    /**
     * Test if a particular [SdkLogMode] is enabled
     */
    public fun isEnabled(mode: SdkLogMode): Boolean = mask and mode.mask != 0

    public companion object {
        /**
         * Get a list of all modes
         */
        public fun allModes(): List<SdkLogMode> = listOf(
            LogRequest,
            LogRequestWithBody,
            LogResponse,
            LogResponseWithBody,
        )
    }

    override fun toString(): String =
        allModes().joinToString(separator = "|", prefix = "SdkLogMode(", postfix = ")") { mode ->
            if (isEnabled(mode)) mode.toString() else ""
        }
}
