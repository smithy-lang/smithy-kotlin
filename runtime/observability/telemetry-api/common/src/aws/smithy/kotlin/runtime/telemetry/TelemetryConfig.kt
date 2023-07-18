/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry

/**
 * Common configuration options for configuring telemetry of a component
 */
public interface TelemetryConfig {
    /**
     * The [TelemetryProvider] to instrument the component with.
     */
    public val telemetryProvider: TelemetryProvider

    public interface Builder {
        /**
         * The [TelemetryProvider] to instrument the component with.
         */
        public var telemetryProvider: TelemetryProvider?
    }
}
