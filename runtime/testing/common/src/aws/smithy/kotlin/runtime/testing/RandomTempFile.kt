/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.testing

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.Uuid

/**
 * Creates a temporary file with random data
 */
@OptIn(InternalApi::class)
public expect class RandomTempFile(
    sizeInBytes: Long,
    filename: String = Uuid.random().toString(),
    binaryData: Boolean = false,
)
