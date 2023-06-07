/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.metrics

/**
 * Factory for creating instruments for recording measurements.
 */
public interface Meter {

    /**
     * Create a new [UpDownCounter]
     *
     * @param name the instrument name
     * @param units the unit of measure
     * @param description the human-readable description of the measurement
     */
    public fun createUpDownCounter(
        name: String,
        units: String? = null,
        description: String? = null,
    ): UpDownCounter

    /**
     * Create a new [MonotonicCounter]
     *
     * @param name the instrument name
     * @param units the unit of measure
     * @param description the human-readable description of the measurement
     */
    public fun createMonotonicCounter(
        name: String,
        units: String? = null,
        description: String? = null,
    ): MonotonicCounter

    /**
     * Create a new [LongHistogram]
     *
     * @param name the instrument name
     * @param units the unit of measure
     * @param description the human-readable description of the measurement
     */
    public fun createLongHistogram(
        name: String,
        units: String? = null,
        description: String? = null,
    ): LongHistogram

    /**
     * Create a new [DoubleHistogram]
     *
     * @param name the instrument name
     * @param units the unit of measure
     * @param description the human-readable description of the measurement
     */
    public fun createDoubleHistogram(
        name: String,
        units: String? = null,
        description: String? = null,
    ): DoubleHistogram

    /**
     * Create a new Gauge.
     *
     * @param name the instrument name
     * @param callback the callback to invoke when reading the gauge value
     * @param units the unit of measure
     * @param description the human-readable description of the measurement
     * @return a [GaugeHandle] which can be used for de-registering the gauge
     * and stopping the callback from being invoked.
     */
    public fun createLongGauge(
        name: String,
        callback: LongGaugeCallback,
        units: String? = null,
        description: String? = null,
    ): GaugeHandle

    /**
     * Create a new Gauge.
     *
     * @param name the instrument name
     * @param callback the callback to invoke when reading the gauge value
     * @param units the unit of measure
     * @param description the human-readable description of the measurement
     * @return a [GaugeHandle] which can be used for de-registering the gauge
     * and stopping the callback from being invoked.
     */
    public fun createDoubleGauge(
        name: String,
        callback: DoubleGaugeCallback,
        units: String? = null,
        description: String? = null,
    ): GaugeHandle
}
