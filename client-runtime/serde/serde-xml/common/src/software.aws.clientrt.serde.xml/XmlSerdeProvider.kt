/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.SerdeProvider
import software.aws.clientrt.serde.Serializer

/**
 * XML serde provider
 */
class XmlSerdeProvider : SerdeProvider {
    override fun serializer(): Serializer = XmlSerializer()
    override fun deserializer(payload: ByteArray): Deserializer = XmlDeserializer(payload)
}
