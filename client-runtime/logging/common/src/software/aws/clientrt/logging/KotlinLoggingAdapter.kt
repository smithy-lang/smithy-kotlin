/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.logging

import mu.KLogger

/**
 * Adapter for kotlin-logging:KLogger
 */
internal class KotlinLoggingAdapter(name: String) : Logger {
    private val log: KLogger = mu.KotlinLogging.logger(name)

    override fun trace(msg: () -> Any?) {
        log.trace(msg)
    }

    override fun trace(t: Throwable?, msg: () -> Any?) {
        log.trace(t, msg)
    }

    override fun debug(msg: () -> Any?) {
        log.debug(msg)
    }

    override fun debug(t: Throwable?, msg: () -> Any?) {
        log.debug(t, msg)
    }

    override fun info(msg: () -> Any?) {
        log.info(msg)
    }

    override fun info(t: Throwable?, msg: () -> Any?) {
        log.info(t, msg)
    }

    override fun warn(msg: () -> Any?) {
        log.warn(msg)
    }

    override fun warn(t: Throwable?, msg: () -> Any?) {
        log.warn(t, msg)
    }

    override fun error(msg: () -> Any?) {
        log.error(msg)
    }

    override fun error(t: Throwable?, msg: () -> Any?) {
        log.error(t, msg)
    }

    override fun <T : Throwable> throwing(throwable: T): T {
        return log.throwing(throwable)
    }

    override fun <T : Throwable> catching(throwable: T) {
        log.catching(throwable)
    }
}
