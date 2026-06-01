/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.mapErr

/**
 * Exception class for all serialization errors
 */
public class SerializationException : ClientException {

    public constructor() : super()

    public constructor(message: String?) : super(message)

    public constructor(message: String?, cause: Throwable?) : super(message, cause)

    public constructor(cause: Throwable?) : super(cause)
}

/**
 * Exception class for all deserialization errors
 */
public open class DeserializationException : ClientException {

    public constructor() : super()

    public constructor(message: String?) : super(message)

    public constructor(message: String?, cause: Throwable?) : super(message, cause)

    public constructor(cause: Throwable?) : super(cause)
}

/**
 * Exception thrown when deserialization exceeds the maximum allowed recursion depth.
 */
public class DeserializationRecursionException : DeserializationException(
    "Max recursion depth ($MAX_RECURSION_DEPTH) exceeded during deserialization",
) {
    @InternalApi
    public companion object {
        /**
         * Compares [currentDepth] to [MAX_RECURSION_DEPTH] and throws [DeserializationRecursionException] if the former
         * exceeds the latter. Otherwise, returns silently.
         */
        @InternalApi
        public fun assertDepth(currentDepth: Int) {
            if (currentDepth > MAX_RECURSION_DEPTH) {
                throw DeserializationRecursionException()
            }
        }
    }
}

/**
 * Get the underlying [success][Result.isSuccess] value or wrap the failure in a [DeserializationException]
 * and throw it.
 */
@InternalApi
public inline fun <T> Result<T>.getOrDeserializeErr(errorMessage: () -> String): T = mapErr { DeserializationException(errorMessage(), it) }
    .getOrThrow()
