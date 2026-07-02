/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client

/**
 * Configuration for HTTP header redaction in debug logging.
 */
public interface LogRedactionConfig {
    /**
     * Set of HTTP header names whose values will be replaced with "<REDACTED>" in
     * request/response debug logging. Matching is case-insensitive. Empty by default
     * (no headers are redacted).
     */
    public val logRedactedHeaders: Set<String>

    /**
     * Configure header redaction for debug logging.
     */
    public interface Builder {
        /**
         * Set of HTTP header names whose values will be replaced with "<REDACTED>" in
         * request/response debug logging. Matching is case-insensitive. Empty by default
         * (no headers are redacted).
         */
        public var logRedactedHeaders: Set<String>?
    }
}
