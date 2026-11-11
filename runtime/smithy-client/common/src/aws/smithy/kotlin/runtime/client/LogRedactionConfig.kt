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
     * Set of HTTP header names whose values will be replaced with `"*** Sensitive Data Redacted ***"`
     * in request/response debug logging. Matching is case-insensitive. This parameter is empty by
     * default, meaning no headers are redacted. You may choose a preselected set of headers such as
     * [PotentiallySensitiveHeaders][aws.smithy.kotlin.runtime.http.PotentiallySensitiveHeaders] or
     * provide your own custom set.
     */
    public val logRedactedHeaders: Set<String>

    /**
     * Configure header redaction for debug logging.
     */
    public interface Builder {
        /**
         * Set of HTTP header names whose values will be replaced with `"*** Sensitive Data Redacted ***"`
         * in request/response debug logging. Matching is case-insensitive. This parameter is empty by
         * default, meaning no headers are redacted. You may choose a preselected set of headers such as
         * [PotentiallySensitiveHeaders][aws.smithy.kotlin.runtime.http.PotentiallySensitiveHeaders] or
         * provide your own custom set.
         */
        public var logRedactedHeaders: MutableSet<String>
    }
}
