/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

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
     */
    public fun getOrCreateTracer(scope: String): Tracer
}
