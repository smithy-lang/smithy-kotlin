/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.testing

/**
 * Creates a temporary file with random data
 */
public expect class RandomTempFile(
    sizeInBytes: Long,
    filename: String = randomFilename(),
    binaryData: Boolean = false,
)

internal expect fun randomFilename(): String
