/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.logging

/**
 * An abstract implementation of a logger provider. By default, this class uses no-op implementations for all members
 * unless overridden in a subclass.
 */
public abstract class AbstractLoggerProvider : LoggerProvider {
    override fun getOrCreateLogger(name: String): Logger = Logger.None
}
