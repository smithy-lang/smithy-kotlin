/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.ClientException

/**
 * Indicates some failure happened while retrying.
 * @param message A message indicating the failure mode.
 * @param cause An underlying exception which caused this exception.
 * @param attempts The number of attempts made before this failure was encountered.
 * @param lastResponse The last response received from the retry strategy before this failure was encountered.
 * @param lastException The exception caught by the retry strategy before this failure was encountered. Note that the
 * [Throwable] in this parameter isn't necessarily the cause of this exception.
 */
public sealed class RetryException(
    message: String,
    cause: Throwable?,
    public val attempts: Int,
    public val lastResponse: Any?,
    public val lastException: Throwable?,
) : ClientException(message, cause)

/**
 * Indicates that retrying has failed because too many attempts have completed unsuccessfully.
 * @param message A message indicating the failure mode.
 * @param attempts The number of attempts made before this failure was encountered.
 * @param lastResponse The last response received from the retry strategy before this failure was encountered.
 * @param lastException The exception caught by the retry strategy before this failure was encountered. Note that the
 * [Throwable] in this parameter isn't necessarily the cause of this exception.
 */
public class TooManyAttemptsException(
    message: String,
    cause: Throwable?,
    attempts: Int,
    lastResponse: Any?,
    lastException: Throwable?,
) : RetryException(message, cause, attempts, lastResponse, lastException)

/**
 * Indicates that retrying has failed because too much time has elapsed.
 * @param message A message indicating the failure mode.
 * @param attempts The number of attempts made before this failure was encountered.
 * @param lastResponse The last response received from the retry strategy before this failure was encountered.
 * @param lastException The exception caught by the retry strategy before this failure was encountered. Note that the
 * [Throwable] in this parameter isn't necessarily the cause of this exception.
 */
public class TimedOutException(
    message: String,
    attempts: Int,
    lastResponse: Any?,
    lastException: Throwable?,
) : RetryException(message, null, attempts, lastResponse, lastException)

/**
 * Indicates the retrying has failed because of a non-retryable condition.
 * @param message A message indicating the failure mode.
 * @param cause An underlying exception which caused this exception.
 * @param attempts The number of attempts made before this failure was encountered.
 * @param lastResponse The last response received from the retry strategy before this failure was encountered.
 */
public class RetryFailureException(
    message: String,
    cause: Throwable?,
    attempts: Int,
    lastResponse: Any?,
) : RetryException(message, cause, attempts, lastResponse, null)
