/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.tracing

/**
 * Configuration for a client that provides tracing capabilities.
 */
public interface TracingClientConfig {
    /**
     * The tracer that is responsible for creating trace spans and wiring them up to a tracing backend (e.g., a trace
     * probe). By default, a standard tracer that uses the service name for the root trace span and
     * delegates to a logging trace probe (i.e., `DefaultTracer(LoggingTraceProbe, "<service-name>")`) is used.
     */
    public val tracer: Tracer

    public interface Builder {
        /**
         * The tracer that is responsible for creating trace spans and wiring them up to a tracing backend (e.g.,
         * a trace probe). By default, a standard tracer that uses the service name for the root
         * trace span and delegates to a logging trace probe
         * (i.e., `DefaultTracer(LoggingTraceProbe, "<service-name>")`) is used.
         */
        public var tracer: Tracer?
    }
}
