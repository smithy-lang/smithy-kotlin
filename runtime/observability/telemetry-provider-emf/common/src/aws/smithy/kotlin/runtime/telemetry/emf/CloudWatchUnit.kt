/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

/**
 * CloudWatch metric units supported by EMF.
 * See: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch_Embedded_Metric_Format_Specification.html
 */
public enum class CloudWatchUnit(public val value: String) {
    SECONDS("Seconds"),
    MICROSECONDS("Microseconds"),
    MILLISECONDS("Milliseconds"),
    BYTES("Bytes"),
    KILOBYTES("Kilobytes"),
    MEGABYTES("Megabytes"),
    GIGABYTES("Gigabytes"),
    TERABYTES("Terabytes"),
    BITS("Bits"),
    KILOBITS("Kilobits"),
    MEGABITS("Megabits"),
    GIGABITS("Gigabits"),
    TERABITS("Terabits"),
    PERCENT("Percent"),
    COUNT("Count"),
    BYTES_PER_SECOND("Bytes/Second"),
    KILOBYTES_PER_SECOND("Kilobytes/Second"),
    MEGABYTES_PER_SECOND("Megabytes/Second"),
    GIGABYTES_PER_SECOND("Gigabytes/Second"),
    TERABYTES_PER_SECOND("Terabytes/Second"),
    BITS_PER_SECOND("Bits/Second"),
    KILOBITS_PER_SECOND("Kilobits/Second"),
    MEGABITS_PER_SECOND("Megabits/Second"),
    GIGABITS_PER_SECOND("Gigabits/Second"),
    TERABITS_PER_SECOND("Terabits/Second"),
    COUNT_PER_SECOND("Count/Second"),
    NONE("None"),
}

/**
 * Maps OTel-style unit strings to CloudWatch units.
 * Logs a warning for unrecognized units so new metrics with unmapped units
 * are visible during development rather than silently degrading.
 */
internal fun mapUnitToCloudWatch(unit: String?): CloudWatchUnit = when (unit) {
    null -> CloudWatchUnit.NONE
    "s" -> CloudWatchUnit.SECONDS
    "ms" -> CloudWatchUnit.MILLISECONDS
    "us" -> CloudWatchUnit.MICROSECONDS
    "By", "bytes" -> CloudWatchUnit.BYTES
    "KBy" -> CloudWatchUnit.KILOBYTES
    "MBy" -> CloudWatchUnit.MEGABYTES
    "GBy" -> CloudWatchUnit.GIGABYTES
    "{attempt}", "{error}", "{request}" -> CloudWatchUnit.COUNT
    "%" -> CloudWatchUnit.PERCENT
    else -> {
        emfLog("[WARN] Unmapped OTel unit '$unit' — defaulting to None. Consider adding a mapping.")
        CloudWatchUnit.NONE
    }
}
