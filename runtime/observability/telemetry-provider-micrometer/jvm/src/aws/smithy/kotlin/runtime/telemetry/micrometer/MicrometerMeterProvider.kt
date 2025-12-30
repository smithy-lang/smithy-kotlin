/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.micrometer

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.metrics.AsyncMeasurementHandle
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleAsyncMeasurement
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleGaugeCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.LongAsyncMeasurement
import aws.smithy.kotlin.runtime.telemetry.metrics.LongGaugeCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.LongHistogram
import aws.smithy.kotlin.runtime.telemetry.metrics.LongUpDownCounterCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.Meter
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MonotonicCounter
import aws.smithy.kotlin.runtime.telemetry.metrics.UpDownCounter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Counter as MicrometerCounter
import io.micrometer.core.instrument.Gauge as MicrometerGauge

internal class MicrometerMeterProvider(private val meterRegistry: MeterRegistry) : MeterProvider {
    override fun getOrCreateMeter(scope: String): Meter = MicrometerMeter(meterRegistry, Tags.of(Tag.of("scope", scope)))
}

private class MicrometerMeter(
    private val meterRegistry: MeterRegistry,
    private val extraTags: Tags,
) : Meter {
    override fun createUpDownCounter(name: String, units: String?, description: String?): UpDownCounter =
        MicrometerUpDownCounter(
            meterMetadata = MeterMetadata(name, units, description, extraTags),
            meterRegistry = meterRegistry,
        )

    override fun createAsyncUpDownCounter(
        name: String,
        callback: LongUpDownCounterCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle =
        MicrometerLongGauge(
            callback = callback,
            meterMetadata = MeterMetadata(name, units, description, extraTags),
            meterRegistry = meterRegistry,
        )

    override fun createMonotonicCounter(name: String, units: String?, description: String?): MonotonicCounter =
        MicrometerMonotonicCounter(
            meterMetadata = MeterMetadata(name, units, description, extraTags),
            meterRegistry = meterRegistry,
        )

    override fun createLongHistogram(name: String, units: String?, description: String?): LongHistogram =
        MicrometerLongHistogram(
            MicrometerDoubleHistogram(
                meterMetadata = MeterMetadata(name, units, description, extraTags),
                meterRegistry = meterRegistry,
            ),
        )

    override fun createDoubleHistogram(name: String, units: String?, description: String?): DoubleHistogram =
        MicrometerDoubleHistogram(
            meterMetadata = MeterMetadata(name, units, description, extraTags),
            meterRegistry = meterRegistry,
        )

    override fun createLongGauge(
        name: String,
        callback: LongGaugeCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle = MicrometerLongGauge(
        callback = callback,
        meterMetadata = MeterMetadata(name, units, description, extraTags),
        meterRegistry = meterRegistry,
    )

    override fun createDoubleGauge(
        name: String,
        callback: DoubleGaugeCallback,
        units: String?,
        description: String?,
    ): AsyncMeasurementHandle = MicrometerDoubleGauge(
        callback = callback,
        meterMetadata = MeterMetadata(name, units, description, extraTags),
        meterRegistry = meterRegistry,
    )
}

private data class MeterMetadata(
    val meterName: String,
    val units: String?,
    val description: String?,
    val extraTags: Tags,
)

private class MicrometerUpDownCounter(
    private val meterMetadata: MeterMetadata,
    private val meterRegistry: MeterRegistry,
) : UpDownCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {
        meterMetadata
            .counter()
            .tags(attributes.toTags())
            .register(meterRegistry)
            .increment(value.toDouble())
    }
}

private class MicrometerMonotonicCounter(
    private val meterMetadata: MeterMetadata,
    private val meterRegistry: MeterRegistry,
) : MonotonicCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {
        if (value < 0) {
            // do nothing on negative value
            return
        }
        meterMetadata
            .counter()
            .tags(attributes.toTags())
            .register(meterRegistry)
            .increment(value.toDouble())
    }
}

private class MicrometerDoubleHistogram(
    private val meterMetadata: MeterMetadata,
    private val meterRegistry: MeterRegistry,
) : DoubleHistogram {
    override fun record(value: Double, attributes: Attributes, context: Context?) {
        DistributionSummary.builder(meterMetadata.meterName)
            .baseUnit(meterMetadata.units)
            .description(meterMetadata.description)
            .tags(meterMetadata.extraTags)
            .tags(attributes.toTags())
            .publishPercentileHistogram()
            .register(meterRegistry)
            .record(value)
    }
}

private class MicrometerLongHistogram(
    private val doubleHistogram: MicrometerDoubleHistogram,
) : LongHistogram {
    override fun record(value: Long, attributes: Attributes, context: Context?) {
        doubleHistogram.record(value.toDouble(), attributes, context)
    }
}

private class MicrometerGaugeDoubleAsyncMeasurement : DoubleAsyncMeasurement {
    private var _value: Double? = null
    private var _tags: Tags? = null
    val value: Double get() = checkNotNull(_value) { "The value has not yet been measured" }
    val tags: Tags get() = checkNotNull(_tags) { "The value has not yet been measured" }

    override fun record(value: Double, attributes: Attributes, context: Context?) {
        _value = value
        _tags = attributes.toTags()
    }
}

private class MicrometerDoubleGauge(
    private val meterMetadata: MeterMetadata,
    private val meterRegistry: MeterRegistry,
    private val callback: DoubleGaugeCallback,
) : AsyncMeasurementHandle {

    private val gauge = MicrometerGaugeDoubleAsyncMeasurement()
        .apply(callback)
        .let { measurement ->
            MicrometerGauge
                .builder(meterMetadata.meterName) { measurement.apply(callback).value }
                .baseUnit(meterMetadata.units)
                .description(meterMetadata.description)
                .tags(meterMetadata.extraTags)
                .tags(measurement.tags)
                .strongReference(true)
                .register(meterRegistry)
        }

    override fun stop() {
        gauge.close()
        meterRegistry.remove(gauge)
    }
}

private class MicrometerGaugeLongAsyncMeasurement(
    private val doubleAsyncMeasurement: DoubleAsyncMeasurement,
) : LongAsyncMeasurement {
    override fun record(value: Long, attributes: Attributes, context: Context?) {
        doubleAsyncMeasurement.record(value.toDouble(), attributes, context)
    }
}

private class MicrometerLongGauge(
    private val callback: LongGaugeCallback,
    meterMetadata: MeterMetadata,
    meterRegistry: MeterRegistry,
) : AsyncMeasurementHandle {

    private val gauge = MicrometerDoubleGauge(meterMetadata, meterRegistry) {
        callback.invoke(MicrometerGaugeLongAsyncMeasurement(it))
    }

    override fun stop() = gauge.stop()
}

private fun MeterMetadata.counter() = MicrometerCounter.builder(meterName)
    .baseUnit(units)
    .description(description)
    .tags(extraTags)

@Suppress("UNCHECKED_CAST")
private fun Attributes.toTags() = keys.mapNotNull {
    val attributeKey = it as? AttributeKey<Any> ?: return@mapNotNull null
    Tag.of(attributeKey.name, getOrNull(attributeKey).toString())
}.let(Tags::of)
