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
     * The name of this client, which will be used in tracing data. If using multiple clients for the same service
     * simultaneously, giving them unique names can help disambiguate them in logging messages or metrics. By default,
     * the client name will be the same as the service name.
     */
    public val clientName: String

    /**
     * The probe that receives tracing events such as logging messages and metrics. This probe can be used to send
     * tracing events to other frameworks outside the SDK. By default, a no-op probe is selected.
     */
    public val traceProbe: TraceProbe
        get() = KotlinLoggingTraceProbe
}
