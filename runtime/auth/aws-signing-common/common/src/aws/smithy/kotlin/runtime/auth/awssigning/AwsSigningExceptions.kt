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
 * @param isSigV4a Whether the unsupported signing algorithm is sigV4a
 */
@InternalApi
public class UnsupportedSigningAlgorithmException(
    message: String,
    public val signingAlgorithm: AwsSigningAlgorithm,
) : ClientException(message)
