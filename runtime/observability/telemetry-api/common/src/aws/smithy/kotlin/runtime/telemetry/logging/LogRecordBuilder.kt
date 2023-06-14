/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.logging

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.get

/**
 * Construct a logging record and emits it to an underlying logger
 */
public interface LogRecordBuilder {
    /**
     * Set the logging level (severity)
     * @param level the level to log this record at
     */
    public fun setLevel(level: LogLevel)

    /**
     * Set the timestamp
     * @param ts the observed time for this event
     */
    public fun setTimestamp(ts: Instant)

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
    public fun setMessage(message: () -> Any)

    /**
     * Set an attribute associated with this log record
     * @param key the key to use
     * @param value the value to associate with [key]
     */
    public fun setAttribute(name: String, value: Any)

    /**
     * Set the telemetry context to associate with this log record
     * @param context the context to associate
     */
    public fun setContext(context: Context)

    /**
     * Associate all key/value pairs from [attributes] with this log record
     * @param attributes the attributes to associate with this log record
     */
    public fun mergeAttributes(attributes: Attributes) {
        attributes.keys.forEach { key ->
            @Suppress("UNCHECKED_CAST")
            setAttribute(key.name, attributes[key as AttributeKey<Any>])
        }
    }

    /**
     * Emit this event to the underlying logger
     */
    public fun emit()
}
