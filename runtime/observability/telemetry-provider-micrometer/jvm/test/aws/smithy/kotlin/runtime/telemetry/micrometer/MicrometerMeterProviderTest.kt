/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.micrometer

import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.attributesOf
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleAsyncMeasurement
import aws.smithy.kotlin.runtime.telemetry.metrics.DoubleGaugeCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.LongAsyncMeasurement
import aws.smithy.kotlin.runtime.telemetry.metrics.LongGaugeCallback
import aws.smithy.kotlin.runtime.telemetry.metrics.LongUpDownCounterCallback
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class MicrometerMeterProviderTest {

    private val scope = "meter.test"
    private val meterRegistry = SimpleMeterRegistry()
    private val meter = MicrometerMeterProvider(meterRegistry).getOrCreateMeter(scope)

    private val attributeOne = AttributeKey<String>("attributeOne")
    private val attributeOneValue = "attributeOneValue"

    private val attributeTwo = AttributeKey<Int>("attributeTwo")
    private val attributeTwoValue = 2

    val attributes = attributesOf {
        attributeOne to attributeOneValue
        attributeTwo to attributeTwoValue
    }
    val expectedTags = listOf(
        Tag.of(attributeOne.name, attributeOneValue),
        Tag.of(attributeTwo.name, "$attributeTwoValue"),
        Tag.of("scope", scope),
    )

    @AfterEach
    fun tearDown() {
        meterRegistry.clear()
    }

    @Test
    fun `upDownCounter should be able to increment and decrement value`() {
        // given
        val upDownCounter = meter.createUpDownCounter(
            name = "testUpDownCounter",
            units = "upDownCounterUnit",
            description = "upDownCounterDescription",
        )

        // when
        upDownCounter.add(value = 3, attributes = attributes)

        // then
        val micrometerCounter = meterRegistry.meters.single() as Counter
        micrometerCounter.id.asClue {
            it.tags shouldContainExactlyInAnyOrder expectedTags
            it.name shouldBe "testUpDownCounter"
            it.baseUnit shouldBe "upDownCounterUnit"
            it.description shouldBe "upDownCounterDescription"
        }

        // and
        micrometerCounter.count() shouldBe 3.0

        // and when
        upDownCounter.add(value = -2, attributes = attributes)

        // then
        micrometerCounter.count() shouldBe 1.0
    }

    @Test
    fun `async upDownCounter should be able to increment and decrement value`() {
        // given
        val measuredValue = AtomicLong(0)
        val asyncMeasurementHandle = meter.createAsyncUpDownCounter(
            name = "testAsyncUpDownCounter",
            callback = object : LongUpDownCounterCallback {
                override fun invoke(measurement: LongAsyncMeasurement) {
                    measurement.record(value = measuredValue.get(), attributes = attributes)
                }
            },
            units = "asyncUpDownCounterUnit",
            description = "asyncUpDownCounterDescription",
        )

        try {
            // when
            measuredValue.set(3)

            // then
            val micrometerGauge = meterRegistry.meters.single() as Gauge
            micrometerGauge.id.asClue {
                it.tags shouldContainExactlyInAnyOrder expectedTags
                it.name shouldBe "testAsyncUpDownCounter"
                it.baseUnit shouldBe "asyncUpDownCounterUnit"
                it.description shouldBe "asyncUpDownCounterDescription"
            }

            // and
            micrometerGauge.measure()
            micrometerGauge.value() shouldBe 3.0

            // and when
            measuredValue.set(1)

            // then
            micrometerGauge.measure()
            micrometerGauge.value() shouldBe 1.0
        } finally {
            asyncMeasurementHandle.stop()
        }
    }

    @Test
    fun `monotonicCounter should be able to increment value`() {
        // given
        val monotonicCounter = meter.createMonotonicCounter(
            name = "monotonicCounter",
            units = "monotonicCounterUnit",
            description = "monotonicCounterDescription",
        )

        // when
        monotonicCounter.add(value = 3, attributes = attributes)

        // then
        val micrometerCounter = meterRegistry.meters.single() as Counter
        micrometerCounter.id.asClue {
            it.tags shouldContainExactlyInAnyOrder expectedTags
            it.name shouldBe "monotonicCounter"
            it.baseUnit shouldBe "monotonicCounterUnit"
            it.description shouldBe "monotonicCounterDescription"
        }

        // and
        micrometerCounter.count() shouldBe 3.0
    }

    @Test
    fun `monotonicCounter should not allow decrementing value`() {
        // given
        val monotonicCounter = meter.createMonotonicCounter(
            name = "monotonicCounter",
            units = "monotonicCounterUnit",
            description = "monotonicCounterDescription",
        )
        monotonicCounter.add(value = 3, attributes = attributes)

        // when
        monotonicCounter.add(value = -2, attributes = attributes)

        // then
        val micrometerCounter = meterRegistry.meters.single() as Counter
        micrometerCounter.id.asClue {
            it.tags shouldContainExactlyInAnyOrder expectedTags
            it.name shouldBe "monotonicCounter"
            it.baseUnit shouldBe "monotonicCounterUnit"
            it.description shouldBe "monotonicCounterDescription"
        }

        // and
        micrometerCounter.count() shouldBe 3.0
    }

    @Test
    fun `longHistogram should be able to record distribution`() {
        // given
        val longHistogram = meter.createLongHistogram(
            name = "testLongHistogram",
            units = "longHistogramUnit",
            description = "longHistogramDescription",
        )

        // when
        longHistogram.record(value = 2, attributes = attributes)
        longHistogram.record(value = 4, attributes = attributes)

        // then
        val micrometerDistribution = meterRegistry.meters.single() as DistributionSummary
        micrometerDistribution.id.asClue {
            it.tags shouldContainExactlyInAnyOrder expectedTags
            it.name shouldBe "testLongHistogram"
            it.baseUnit shouldBe "longHistogramUnit"
            it.description shouldBe "longHistogramDescription"
        }

        // and
        micrometerDistribution.asClue {
            it.count() shouldBe 2
            it.max() shouldBe 4.0
            it.mean() shouldBe 3.0
            it.totalAmount() shouldBe 6.0
        }
    }

    @Test
    fun `doubleHistogram should be able to record distribution`() {
        // given
        val doubleHistogram = meter.createDoubleHistogram(
            name = "testDoubleHistogram",
            units = "unit",
            description = "description",
        )

        // when
        doubleHistogram.record(value = 2.0, attributes = attributes)
        doubleHistogram.record(value = 4.0, attributes = attributes)

        // then
        val micrometerDistribution = meterRegistry.meters.single() as DistributionSummary
        micrometerDistribution.id.asClue {
            it.tags shouldContainExactlyInAnyOrder expectedTags
            it.name shouldBe "testDoubleHistogram"
            it.baseUnit shouldBe "unit"
            it.description shouldBe "description"
        }

        // and
        micrometerDistribution.asClue {
            it.count() shouldBe 2
            it.max() shouldBe 4.0
            it.mean() shouldBe 3.0
            it.totalAmount() shouldBe 6.0
        }
    }

    @Test
    fun `longGauge should be able to measure value`() {
        // given
        val measuredValue = AtomicLong(0)
        val asyncMeasurementHandle = meter.createLongGauge(
            name = "testLongGauge",
            callback = object : LongGaugeCallback {
                override fun invoke(measurement: LongAsyncMeasurement) {
                    measurement.record(value = measuredValue.get(), attributes = attributes)
                }
            },
            units = "unit",
            description = "description",
        )

        try {
            // when
            measuredValue.set(3)

            // then
            val micrometerGauge = meterRegistry.meters.single() as Gauge
            micrometerGauge.id.asClue {
                it.tags shouldContainExactlyInAnyOrder expectedTags
                it.name shouldBe "testLongGauge"
                it.baseUnit shouldBe "unit"
                it.description shouldBe "description"
            }

            // and
            micrometerGauge.measure()
            micrometerGauge.value() shouldBe 3.0

            // and when
            measuredValue.set(1)

            // then
            micrometerGauge.measure()
            micrometerGauge.value() shouldBe 1.0
        } finally {
            asyncMeasurementHandle.stop()
        }
    }

    @Test
    fun `doubleGauge should be able to measure value`() {
        // given
        val measuredValue = AtomicReference(0.0)
        val asyncMeasurementHandle = meter.createDoubleGauge(
            name = "testDoubleGauge",
            callback = object : DoubleGaugeCallback {
                override fun invoke(measurement: DoubleAsyncMeasurement) {
                    measurement.record(value = measuredValue.get(), attributes = attributes)
                }
            },
            units = "unit",
            description = "description",
        )

        try {
            // when
            measuredValue.set(3.0)

            // then
            val micrometerGauge = meterRegistry.meters.single() as Gauge
            micrometerGauge.id.asClue {
                it.tags shouldContainExactlyInAnyOrder expectedTags
                it.name shouldBe "testDoubleGauge"
                it.baseUnit shouldBe "unit"
                it.description shouldBe "description"
            }

            // and
            micrometerGauge.measure()
            micrometerGauge.value() shouldBe 3.0

            // and when
            measuredValue.set(1.0)

            // then
            micrometerGauge.measure()
            micrometerGauge.value() shouldBe 1.0
        } finally {
            asyncMeasurementHandle.stop()
        }
    }
}
