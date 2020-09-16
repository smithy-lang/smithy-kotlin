/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.json

import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.SerdeProvider
import software.aws.clientrt.serde.Serializer

/**
 * JSON serde provider
 */
class JsonSerdeProvider : SerdeProvider {
    override fun serializer(): Serializer = JsonSerializer()
    override fun deserializer(payload: ByteArray): Deserializer = JsonDeserializer(payload)
}
