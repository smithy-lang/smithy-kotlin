/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.otel

import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.telemetry.metrics.*
import aws.smithy.kotlin.runtime.util.Attributes
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement
import io.opentelemetry.api.metrics.ObservableLongMeasurement
import io.opentelemetry.api.metrics.DoubleHistogram as OtelDoubleHistogram
import io.opentelemetry.api.metrics.LongCounter as OtelLongCounter
import io.opentelemetry.api.metrics.LongHistogram as OtelLongHistogram
import io.opentelemetry.api.metrics.LongUpDownCounter as OtelLongUpDownCounter
import io.opentelemetry.api.metrics.Meter as OpenTelemetryMeter

internal class OtelMeterProvider(private val otel: OpenTelemetry) : MeterProvider {
    override fun getOrCreateMeter(scope: String, attributes: Attributes): Meter {
        val meter = otel.getMeter(scope)
        // FIXME: meter level attributes not supported yet by Java impl
        // https://github.com/open-telemetry/opentelemetry-java/issues/5503
        return OtelMeter(meter)
    }
}

private class OtelMeter(
    private val otelMeter: OpenTelemetryMeter,
) : Meter {
    override fun createUpDownCounter(name: String, units: String?, description: String?): UpDownCounter {
        val counter = otelMeter.upDownCounterBuilder(name)
            .apply {
                description?.let { setDescription(it) }
                units?.let { setUnit(units) }
            }
            .build()
        return OtelUpDownCounterImpl(counter)
    }

    override fun createMonotonicCounter(name: String, units: String?, description: String?): MonotonicCounter {
        val counter = otelMeter.counterBuilder(name)
            .apply {
                description?.let { setDescription(it) }
                units?.let { setUnit(units) }
            }
            .build()
        return OtelMonotonicCounterImpl(counter)
    }

    override fun createLongHistogram(name: String, units: String?, description: String?): LongHistogram {
        val hist = otelMeter.histogramBuilder(name)
            .apply {
                description?.let { setDescription(it) }
                units?.let { setUnit(units) }
            }
            .ofLongs()
            .build()
        return OtelLongHistogramImpl(hist)
    }

    override fun createDoubleHistogram(name: String, units: String?, description: String?): DoubleHistogram {
        val hist = otelMeter.histogramBuilder(name)
            .apply {
                description?.let { setDescription(it) }
                units?.let { setUnit(units) }
            }
            .build()
        return OtelDoubleHistogramImpl(hist)
    }

    override fun createLongGauge(
        name: String,
        callback: LongGaugeCallback,
        units: String?,
        description: String?,
    ): GaugeHandle {
        val observer = otelMeter.gaugeBuilder(name)
            .apply {
                description?.let { setDescription(it) }
                units?.let { setUnit(units) }
            }
            .ofLongs()
            .buildWithCallback {
                callback(OtelLongAsyncMeasurementImpl(it))
            }
        return OtelGaugeHandleImpl(observer)
    }

    override fun createDoubleGauge(
        name: String,
        callback: DoubleGaugeCallback,
        units: String?,
        description: String?,
    ): GaugeHandle {
        val observer = otelMeter.gaugeBuilder(name)
            .apply {
                description?.let { setDescription(it) }
                units?.let { setUnit(units) }
            }
            .buildWithCallback {
                callback(OtelDoubleAsyncMeasurementImpl(it))
            }
        return OtelGaugeHandleImpl(observer)
    }
}

private class OtelUpDownCounterImpl(
    private val instrument: OtelLongUpDownCounter,
) : UpDownCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {
        val otelCtx = (context as? OtelContext)?.context
        if (otelCtx != null) {
            instrument.add(value, attributes.toOtelAttributes(), otelCtx)
        } else {
            instrument.add(value, attributes.toOtelAttributes())
        }
    }
}

private class OtelMonotonicCounterImpl(
    private val instrument: OtelLongCounter,
) : MonotonicCounter {
    override fun add(value: Long, attributes: Attributes, context: Context?) {
        val otelCtx = (context as? OtelContext)?.context
        if (otelCtx != null) {
            instrument.add(value, attributes.toOtelAttributes(), otelCtx)
        } else {
            instrument.add(value, attributes.toOtelAttributes())
        }
    }
}

private class OtelLongHistogramImpl(
    private val instrument: OtelLongHistogram,
) : LongHistogram {
    override fun record(value: Long, attributes: Attributes, context: Context?) {
        val otelCtx = (context as? OtelContext)?.context
        if (otelCtx != null) {
            instrument.record(value, attributes.toOtelAttributes(), otelCtx)
        } else {
            instrument.record(value, attributes.toOtelAttributes())
        }
    }
}

private class OtelDoubleHistogramImpl(
    private val instrument: OtelDoubleHistogram,
) : DoubleHistogram {
    override fun record(value: Double, attributes: Attributes, context: Context?) {
        val otelCtx = (context as? OtelContext)?.context
        if (otelCtx != null) {
            instrument.record(value, attributes.toOtelAttributes(), otelCtx)
        } else {
            instrument.record(value, attributes.toOtelAttributes())
        }
    }
}

private class OtelLongAsyncMeasurementImpl(private val measurement: ObservableLongMeasurement) : LongAsyncMeasurement {
    override fun record(value: Long, attributes: Attributes, context: Context) {
        if (attributes.isEmpty) {
            measurement.record(value)
        } else {
            measurement.record(value, attributes.toOtelAttributes())
        }
    }
}

private class OtelDoubleAsyncMeasurementImpl(private val measurement: ObservableDoubleMeasurement) : DoubleAsyncMeasurement {
    override fun record(value: Double, attributes: Attributes, context: Context) {
        if (attributes.isEmpty) {
            measurement.record(value)
        } else {
            measurement.record(value, attributes.toOtelAttributes())
        }
    }
}

private class OtelGaugeHandleImpl(private val otelHandle: AutoCloseable) : GaugeHandle {
    override fun stop() {
        otelHandle.close()
    }
}
