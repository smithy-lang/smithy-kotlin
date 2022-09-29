/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.tracing.NoOpTraceSpan
import aws.smithy.kotlin.runtime.tracing.TraceSpan

/**
 * Represents a producer/source of AWS credentials
 */
public interface CredentialsProvider {
    /**
     * Request credentials from the provider
     */
    public suspend fun getCredentials(traceSpan: TraceSpan = NoOpTraceSpan): Credentials
}
