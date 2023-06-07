/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * The entry point for creating [Tracer] instances.
 */
public interface TracerProvider {
    public companion object {
        /**
         * A [TracerProvider] that does nothing
         */
        public val None: TracerProvider = NoOpTracerProvider
    }

    /**
     * Returns a unique [Tracer] scoped to be used by instrumentation code. The scope
     * and identity of that instrumentation code is uniquely identified by the name
     * and attributes.
     *
     * @param scope the name of the instrumentation scope
     * @param attributes (optional) specifies the instrumentation scope attributes to associate with emitted
     * telemetry
     */
    public fun getTracer(scope: String, attributes: Attributes = emptyAttributes()): Tracer
}
