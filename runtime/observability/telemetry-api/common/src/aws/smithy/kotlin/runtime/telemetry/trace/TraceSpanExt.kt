/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.util.AttributeKey

/**
 * Set common error attributes from an exception
 * @param ex the exception to record attributes for
 * @param escaped true if this exception escaped the current function
 */
public fun TraceSpan.recordException(ex: Throwable, escaped: Boolean) {
    // https://opentelemetry.io/docs/reference/specification/trace/semantic_conventions/exceptions/
    val exType = ex::class.qualifiedName ?: ex::class.simpleName
    setAttribute("error", true)
    ex.message?.let { setAttribute("exception.message", it) }
    exType?.let { setAttribute("exception.type", it) }
    setAttribute("exception.stacktrace", ex.stackTraceToString())
    ex.cause?.let { setAttribute("exception.cause", it.toString()) }
    setAttribute("exception.escaped", escaped)
}

/**
 * Set an attribute on the span using a string key
 * @param key the attribute key to use
 * @param value the value to associate with the key
 */
public fun <T : Any> TraceSpan.setAttribute(key: String, value: T): Unit =
    set(AttributeKey(key), value)
