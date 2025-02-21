/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi

/**
 * Is thrown when a signing algorithm is not supported by a signer
 *
 * See: [AwsSigningAlgorithm], [AwsSigner]
 *
 * @param message The message displayed by the exception
 * @param signingAlgorithm The unsupported signing algorithm
 * @param cause The cause of the exception
 */
@InternalApi
@Deprecated("This exception is no longer thrown. It will be removed in the next minor version, v1.5.x.")
public class UnsupportedSigningAlgorithmException(
    message: String,
    public val signingAlgorithm: AwsSigningAlgorithm,
    cause: Throwable? = null,
) : ClientException(
    message,
    cause,
) {
    public constructor(
        message: String,
        signingAlgorithm: AwsSigningAlgorithm,
    ) : this (
        message,
        signingAlgorithm,
        null,
    )
}
