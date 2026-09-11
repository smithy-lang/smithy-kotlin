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

    const val DEFAULT_NAMESPACE = "AwsSdk/KotlinSdk"
    const val ENV_LAMBDA_LOG_GROUP = "AWS_LAMBDA_LOG_GROUP_NAME"

    // EMF document constraints, per the JSON schema in
    // https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch_Embedded_Metric_Format_Specification.html
    const val MAX_NAMESPACE_LENGTH = 1024
    const val MAX_METRIC_NAME_LENGTH = 1024
    const val MAX_DIMENSIONS_PER_SET = 30
    const val MAX_DIMENSION_KEY_LENGTH = 250
    const val MAX_DIMENSION_VALUE_LENGTH = 1024

    // CloudWatch Logs service constraint: log group names are 1–512 characters
    // https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_CreateLogGroup.html
    const val MAX_LOG_GROUP_NAME_LENGTH = 512
}
