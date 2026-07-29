/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

internal object EmfConstants {
    const val AWS_METADATA_KEY = "_aws"
    const val TIMESTAMP_KEY = "Timestamp"
    const val LOG_GROUP_NAME_KEY = "LogGroupName"
    const val CLOUDWATCH_METRICS_KEY = "CloudWatchMetrics"
    const val NAMESPACE_KEY = "Namespace"
    const val DIMENSIONS_KEY = "Dimensions"
    const val METRICS_KEY = "Metrics"
    const val METRIC_NAME_KEY = "Name"
    const val METRIC_UNIT_KEY = "Unit"
    const val METRIC_STORAGE_RESOLUTION_KEY = "StorageResolution"

    const val DEFAULT_NAMESPACE = "AwsSdk/KotlinSdk"
    const val EMF_LOGGER_NAME = "aws.sdk.kotlin.metrics.emf"
    const val ENV_LAMBDA_LOG_GROUP = "AWS_LAMBDA_LOG_GROUP_NAME"

    const val MAX_METRICS_PER_DOCUMENT = 100
    const val MAX_DIMENSIONS_PER_SET = 30
    const val MAX_VALUES_PER_METRIC = 100
    const val MAX_NAMESPACE_LENGTH = 1024
    const val MAX_LOG_GROUP_NAME_LENGTH = 512
    const val MAX_METRIC_NAME_LENGTH = 1024
    const val MAX_DIMENSION_KEY_LENGTH = 250
    const val MAX_DIMENSION_VALUE_LENGTH = 1024
}
