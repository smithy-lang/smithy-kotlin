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
     * A connection level error such as a socket timeout, connect error, TLS negotiation timeout, etc. Typically, these
     * should not be retried for non-idempotent requests as it's impossible to know whether the operation had a side
     * effect on the server
     */
    Transient,
}
