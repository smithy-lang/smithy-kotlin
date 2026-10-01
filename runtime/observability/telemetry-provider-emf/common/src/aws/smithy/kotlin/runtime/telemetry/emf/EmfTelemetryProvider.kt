/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.telemetry.emf

import aws.smithy.kotlin.runtime.telemetry.TelemetryProvider
import aws.smithy.kotlin.runtime.telemetry.context.ContextManager
import aws.smithy.kotlin.runtime.telemetry.logging.LoggerProvider
import aws.smithy.kotlin.runtime.telemetry.metrics.MeterProvider
import aws.smithy.kotlin.runtime.telemetry.trace.TracerProvider
import aws.smithy.kotlin.runtime.util.PlatformProvider

/**
 * A [TelemetryProvider] that emits SDK operational metrics in
 * [CloudWatch Embedded Metric Format (EMF)](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch_Embedded_Metric_Format.html).
 *
 * Metrics are written as structured JSON to stdout via `println()`.
 * In environments with built-in CloudWatch Logs integration (AWS Lambda, Amazon ECS), these logs are
 * automatically ingested and metrics are extracted without requiring separate PutMetricData API calls.
 *
 * Example usage:
 * ```kotlin
 * val client = S3Client {
 *     telemetryProvider = EmfTelemetryProvider {
 *         namespace = "MyApp"
 *     }
 * }
 * ```
 */
public class EmfTelemetryProvider private constructor(builder: Builder) : TelemetryProvider {
    override val meterProvider: MeterProvider = EmfMeterProvider(
        namespace = builder.namespace,
        logGroupName = builder.logGroupName,
    )
    override val loggerProvider: LoggerProvider = builder.loggerProvider
    override val tracerProvider: TracerProvider = TracerProvider.None
    override val contextManager: ContextManager = ContextManager.None

    public companion object {
        public operator fun invoke(block: Builder.() -> Unit = {}): EmfTelemetryProvider = Builder().apply(block).build()
    }

    public class Builder {
        /**
         * The CloudWatch namespace for emitted metrics.
         * Default: "AwsSdk/KotlinSdk"
         */
        public var namespace: String = EmfConstants.DEFAULT_NAMESPACE

        /**
         * The CloudWatch Logs log group name to include in the EMF document.
         * In Lambda environments, this defaults to the `AWS_LAMBDA_LOG_GROUP_NAME` environment variable.
         * For non-Lambda environments using the CloudWatch agent, this should be set explicitly.
         * If null, the LogGroupName field is omitted from the EMF document.
         */
        public var logGroupName: String? = PlatformProvider.System.getenv(EmfConstants.ENV_LAMBDA_LOG_GROUP)

        /**
         * The logger provider to use for SDK internal logging (not EMF output).
         * Defaults to LoggerProvider.None.
         */
        public var loggerProvider: LoggerProvider = LoggerProvider.None

        public fun build(): EmfTelemetryProvider {
            require(namespace.isNotEmpty() && namespace.length <= EmfConstants.MAX_NAMESPACE_LENGTH) {
                "EMF namespace must be between 1 and ${EmfConstants.MAX_NAMESPACE_LENGTH} characters, got: '$namespace' (${namespace.length} chars)"
            }
            // CloudWatch Logs service constraint: log group names are 1–512 characters
            // See: https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_CreateLogGroup.html
            logGroupName?.let {
                require(it.isNotEmpty() && it.length <= EmfConstants.MAX_LOG_GROUP_NAME_LENGTH) {
                    "EMF logGroupName must be between 1 and ${EmfConstants.MAX_LOG_GROUP_NAME_LENGTH} characters, got: '$it' (${it.length} chars)"
                }
            }
            return EmfTelemetryProvider(this)
        }
    }
}
