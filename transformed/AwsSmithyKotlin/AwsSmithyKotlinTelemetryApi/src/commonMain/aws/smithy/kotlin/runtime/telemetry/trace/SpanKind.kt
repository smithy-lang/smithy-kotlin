/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.telemetry.trace

/**
 * Indicates whether a span is a remote child or parent from the point of view of the instrumented
 * code.
 */
public enum class SpanKind {
    /**
     * (Default) A span that represents an internal operation within an application
     */
    INTERNAL,

    /**
     * A span that represents a request to a remote service
     */
    CLIENT,

    /**
     * A span that represents a (synchronous) network request handler.
     */
    SERVER,
}
