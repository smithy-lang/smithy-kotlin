/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.time.Instant

/**
 * Construct a logging record that can be emitted to an underlying logger.
 */
public interface LogRecordBuilder {
    /**
     * Set an exception associated with this event
     * Some loggers will do additional formatting for exceptions.
     * @param ex the exception to associate with this log record
     */
    public fun setCause(ex: Throwable)

    /**
     * Set the log message
     * @param message the message to log
     */
    public fun setMessage(message: String)

    /**
     * Set a log message supplier
     * @param message the block of code responsible for supplying a log message. `toString()` will be used on the
     * supplied value.
     */
    public fun setMessage(message: MessageSupplier)

    /**
     * Set a key/value pair to associate with this log record
     * @param key the key to use
     * @param value the value to associate with [key]
     */
    public fun setKeyValuePair(key: String, value: Any)

    /**
     * Set the telemetry context to associate with this log record
     * @param context the context to associate
     */
    public fun setContext(context: Context)

    /**
     * Emit this event to the underlying logger
     */
    public fun emit()
}
