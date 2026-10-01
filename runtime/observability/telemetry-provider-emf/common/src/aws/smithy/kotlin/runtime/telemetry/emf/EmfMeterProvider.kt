/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.telemetry.metrics.Meter
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider

internal class EmfMeterProvider(
    private val namespace: String,
    private val logGroupName: String?,
) : MeterProvider {
    override fun getOrCreateMeter(scope: String): Meter = EmfMeter(namespace, logGroupName)
}
