/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde

/**
 * Factory type for creating new instances of a [Serializer] or [Deserializer]
 */
interface SerdeProvider {
    /**
     * Create a new serializer
     */
    fun serializer(): Serializer

    /**
     * Create a new deserializer for the given payload
     */
    fun deserializer(payload: ByteArray): Deserializer
}
