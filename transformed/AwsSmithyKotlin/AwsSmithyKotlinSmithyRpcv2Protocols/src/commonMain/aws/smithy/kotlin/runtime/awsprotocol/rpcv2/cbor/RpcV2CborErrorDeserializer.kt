/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol.rpcv2.cbor

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.awsprotocol.ErrorDetails
import aws.smithy.kotlin.runtime.awsprotocol.sanitizeErrorType
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.SdkObjectDescriptor
import aws.smithy.kotlin.runtime.serde.SerialKind
import aws.smithy.kotlin.runtime.serde.cbor.CborDeserializer
import aws.smithy.kotlin.runtime.serde.cbor.CborSerialName
import aws.smithy.kotlin.runtime.serde.deserializeStruct

/**
 * Deserialize errors in the RPC V2 CBOR protocol according to the specification:
 * https://smithy.io/2.0/additional-specs/protocols/smithy-rpc-v2.html#operation-error-serialization
 */
@InternalApi
public object RpcV2CborErrorDeserializer {
    private val ERR_CODE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, CborSerialName("__type"))
    private val MESSAGE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, CborSerialName("message"))

    private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        field(ERR_CODE_DESCRIPTOR)
        field(MESSAGE_DESCRIPTOR)
    }

    public fun deserialize(payload: ByteArray?): ErrorDetails {
        var type: String? = null
        var message: String? = null

        if (payload != null) {
            CborDeserializer(payload).deserializeStruct(OBJ_DESCRIPTOR) {
                loop@ while (true) {
                    when (findNextFieldIndex()) {
                        ERR_CODE_DESCRIPTOR.index -> type = deserializeString()
                        MESSAGE_DESCRIPTOR.index -> message = deserializeString()
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        }

        return ErrorDetails(sanitizeErrorType(type), message, requestId = null)
    }
}
