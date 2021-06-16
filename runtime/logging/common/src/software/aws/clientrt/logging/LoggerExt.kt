/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.logging

/**
 * Logger that wraps [inner] and adds key/value pairs from the log context to every
 * logging message. This is a (very) rough MDC/structured logging abstraction
 */
private class ContextualLogger(
    private val inner: Logger,
    logCtx: Map<String, Any>
) : Logger {

    private val formattedCtx = logCtx.entries.joinToString(separator = "; ", postfix = ";") { "${it.key}: ${it.value}" }

    override fun trace(msg: () -> Any?) { inner.trace { "$formattedCtx - ${msg.invoke()}" } }
    override fun trace(t: Throwable?, msg: () -> Any?) { inner.trace(t) { "$formattedCtx - ${msg.invoke()}" } }
    override fun debug(msg: () -> Any?) { inner.debug { "$formattedCtx - ${msg.invoke()}" } }
    override fun debug(t: Throwable?, msg: () -> Any?) { inner.debug(t) { "$formattedCtx - ${msg.invoke()}" } }
    override fun info(msg: () -> Any?) { inner.info { "$formattedCtx - ${msg.invoke()}" } }
    override fun info(t: Throwable?, msg: () -> Any?) { inner.info(t) { "$formattedCtx - ${msg.invoke()}" } }
    override fun warn(msg: () -> Any?) { inner.warn { "$formattedCtx - ${msg.invoke()}" } }
    override fun warn(t: Throwable?, msg: () -> Any?) { inner.warn(t) { "$formattedCtx - ${msg.invoke()}" } }
    override fun error(msg: () -> Any?) { inner.error { "$formattedCtx - ${msg.invoke()}" } }
    override fun error(t: Throwable?, msg: () -> Any?) { inner.error(t) { "$formattedCtx - ${msg.invoke()}" } }

    override fun <T : Throwable> throwing(throwable: T): T = inner.throwing(throwable)
    override fun <T : Throwable> catching(throwable: T) = inner.catching(throwable)
}

// we should probably map this to MDC on JVM but we don't really have equivalents on other platforms anyway
/**
 * Return a new logger with the given key-value pairs as contextual data added to all requests logged
 */
fun Logger.withContext(vararg pairs: Pair<String, Any>): Logger = ContextualLogger(this, pairs.toMap())
