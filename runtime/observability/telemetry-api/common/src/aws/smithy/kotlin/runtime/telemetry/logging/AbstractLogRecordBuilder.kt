/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.logging

/**
 * An abstract implementation of a log record builder. By default, this class uses no-op implementations for all members
 * unless overridden in a subclass.
 */
public abstract class AbstractLogRecordBuilder : LogRecordBuilder {
    override fun setCause(ex: Throwable) { }
    override fun setMessage(message: String) { }
    override fun setMessage(message: MessageSupplier) { }
    override fun setKeyValuePair(key: String, value: Any) { }
    override fun emit() { }
}
