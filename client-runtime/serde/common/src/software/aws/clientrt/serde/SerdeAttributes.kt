/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde

import software.aws.clientrt.util.AttributeKey
import software.aws.clientrt.util.Attributes
import software.aws.clientrt.util.InternalAPI
import software.aws.clientrt.util.get

/**
 * ExecutionContext keys related to serialization/deserialization
 */
public object SerdeAttributes {

    /**
     * The [SerdeProvider] to use for an operation. Typically a mandatory key
     */
    public val SerdeProvider: AttributeKey<SerdeProvider> = AttributeKey("SerdeProvider")
}

/**
 * Convenience function for creating a serializer by pulling the [SerdeAttributes.SerdeProvider] out of the property bag
 */
@InternalAPI
fun Attributes.serializer(): Serializer = get(SerdeAttributes.SerdeProvider).serializer()

/**
 * Convenience function for creating a deserializer by pulling the [SerdeAttributes.SerdeProvider] out of the property bag
 */
@InternalAPI
fun Attributes.deserializer(payload: ByteArray): Deserializer = get(SerdeAttributes.SerdeProvider).deserializer(payload)
