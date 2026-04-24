/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.retries.delay

import kotlin.time.Duration

/**
 * Common configuration properties shared by exponential backoff delay providers.
 */
public interface ExponentialBackoffWithJitterConfig : DelayProvider.Config.Builder {
    public var initialDelay: Duration
    public var scaleFactor: Double
    public var jitter: Double
    public var maxBackoff: Duration
}
