/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

/**
 * Maps the exception to a new error if this instance represents [failure][Result.isFailure], leaving
 * a [success][Result.isSuccess] value untouched.
 */
public inline fun <T> Result<T>.mapErr(onFailure: (Throwable) -> Throwable): Result<T> =
    when (val ex = exceptionOrNull()) {
        null -> this
        else -> Result.failure(onFailure(ex))
    }
