/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.collections.attributesOf
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

@OptIn(ExperimentalApi::class)
class EmfInstrumentsTest {
    @Test
    fun doubleHistogramEmitsEmf() {
        val output = captureEmfOutput {
            val provider = EmfTelemetryProvider {
                namespace = "Test"
                logGroupName = null
            }
            val meter = provider.meterProvider.getOrCreateMeter("test-scope")
            val histogram = meter.createDoubleHistogram("test.duration", "s", "test")

            histogram.record(
                0.5,
                attributesOf {
                    "rpc.service" to "S3"
                    "rpc.method" to "Get"
                },
            )
        }

        assertContains(output, "\"test.duration\":0.5")
        assertContains(output, "\"Unit\":\"Seconds\"")
        assertContains(output, "\"rpc.service\":\"S3\"")
    }

    @Test
    fun longHistogramEmitsEmf() {
        val output = captureEmfOutput {
            val provider = EmfTelemetryProvider {
                namespace = "Test"
                logGroupName = null
            }
            val meter = provider.meterProvider.getOrCreateMeter("test-scope")
            val histogram = meter.createLongHistogram("test.size", "bytes", "test")

            histogram.record(1024L, attributesOf { "rpc.service" to "S3" })
        }

        assertContains(output, "\"test.size\":1024.0")
        assertContains(output, "\"Unit\":\"Bytes\"")
    }

    @Test
    fun monotonicCounterEmitsForPositiveValues() {
        val output = captureEmfOutput {
            val provider = EmfTelemetryProvider {
                namespace = "Test"
                logGroupName = null
            }
            val meter = provider.meterProvider.getOrCreateMeter("test-scope")
            val counter = meter.createMonotonicCounter("test.count", "{request}", "test")

            counter.add(5, attributesOf { "rpc.service" to "DynamoDb" })
        }

        assertContains(output, "\"test.count\":5.0")
        assertContains(output, "\"Unit\":\"Count\"")
    }

    @Test
    fun monotonicCounterIgnoresNegativeValues() {
        val output = captureEmfOutput {
            val provider = EmfTelemetryProvider {
                namespace = "Test"
                logGroupName = null
            }
            val meter = provider.meterProvider.getOrCreateMeter("test-scope")
            val counter = meter.createMonotonicCounter("test.count", "{request}", "test")

            counter.add(-1, emptyAttributes())
        }

        assertTrue(output.isEmpty())
    }

    @Test
    fun upDownCounterEmitsForAllValues() {
        val output = captureEmfOutput {
            val provider = EmfTelemetryProvider {
                namespace = "Test"
                logGroupName = null
            }
            val meter = provider.meterProvider.getOrCreateMeter("test-scope")
            val counter = meter.createUpDownCounter("test.connections", null, "test")

            counter.add(-3, emptyAttributes())
        }

        assertContains(output, "\"test.connections\":-3.0")
    }
}

private fun captureEmfOutput(block: () -> Unit): String {
    val originalOut = System.out
    val baos = java.io.ByteArrayOutputStream()
    System.setOut(java.io.PrintStream(baos))
    try {
        block()
    } finally {
        System.setOut(originalOut)
    }
    return baos.toString().trim()
}
