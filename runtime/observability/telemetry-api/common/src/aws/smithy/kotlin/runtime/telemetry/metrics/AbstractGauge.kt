/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics

/**
 * An abstract implementation of an asynchronous measurement handle. By default, this class uses no-op implementations
 * for all members unless overridden in a subclass.
 */
public abstract class AbstractAsyncMeasurementHandle : AsyncMeasurementHandle {
    override fun stop() { }
}
