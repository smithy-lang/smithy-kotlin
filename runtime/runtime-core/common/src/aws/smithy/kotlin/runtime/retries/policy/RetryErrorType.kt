/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries.policy

/**
 * A type of error that may be retried.
 */
public enum class RetryErrorType {
    /**
     * A general server-side error.
     */
    ServerSide,

    /**
     * A general client-side error.
     */
    ClientSide,

    /**
     * A server-indicated throttling error.
     */
    Throttling,

    /**
     * A timeout condition such as a socket error or server-indicated request timeout.
     */
    Timeout,
}
