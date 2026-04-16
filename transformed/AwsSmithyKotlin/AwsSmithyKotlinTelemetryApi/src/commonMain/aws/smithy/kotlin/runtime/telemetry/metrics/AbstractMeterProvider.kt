/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.metrics

/**
 * An abstract implementation of a meter provider. By default, this class uses no-op implementations for all members
 * unless overridden in a subclass.
 */
public abstract class AbstractMeterProvider : MeterProvider {
    override fun getOrCreateMeter(scope: String): Meter = Meter.None
}
