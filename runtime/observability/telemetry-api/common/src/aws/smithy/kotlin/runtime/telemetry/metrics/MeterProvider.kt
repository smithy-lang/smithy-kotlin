/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.metrics

import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * Entry point for metrics.
 */
public interface MeterProvider {
    public companion object {
        /**
         * A [MeterProvider] that does nothing
         */
        public val None: MeterProvider = NoOpMeterProvider
    }

    /**
     * Get or create a named [Meter]
     * @param scope the name of the instrumentation scope that uniquely identifies this meter
     * @param attributes instrumentation scope attributes to associate with emitted telemetry
     */
    public fun getOrCreateMeter(scope: String, attributes: Attributes = emptyAttributes()): Meter
}
