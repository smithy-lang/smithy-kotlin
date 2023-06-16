/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol.json

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.awsprotocol.ErrorDetails
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.serde.json.JsonDeserializer
import aws.smithy.kotlin.runtime.serde.json.JsonSerialName

// header identifying the error code
public const val X_AMZN_ERROR_TYPE_HEADER_NAME: String = "X-Amzn-Errortype"

// returned by RESTFUL services that do no send a payload (like in a HEAD request)
public const val X_AMZN_ERROR_MESSAGE_HEADER_NAME: String = "x-amzn-error-message"

// error message header returned by event stream errors
public const val X_AMZN_EVENT_ERROR_MESSAGE_HEADER_NAME: String = ":error-message"

/**
 * Deserializes rest JSON protocol errors as specified by:
 *     - Smithy spec: https://awslabs.github.io/smithy/1.0/spec/aws/aws-restjson1-protocol.html#operation-error-serialization
 *     - SDK Unmarshal Service API Errors (SEP)
 *     - x-amzn-ErrorMessage (SEP)
 */
@InternalApi
public object RestJsonErrorDeserializer {
    // alternative field descriptors for error codes embedded in the document
    private val ERR_CODE_ALT1_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("code"))
    private val ERR_CODE_ALT2_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("__type"))

    // alternative field descriptors for the error message embedded in the document
    private val MESSAGE_ALT1_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("message"))
    private val MESSAGE_ALT2_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("Message"))
    private val MESSAGE_ALT3_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("errorMessage"))

    private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        field(ERR_CODE_ALT1_DESCRIPTOR)
        field(ERR_CODE_ALT2_DESCRIPTOR)
        field(MESSAGE_ALT1_DESCRIPTOR)
        field(MESSAGE_ALT2_DESCRIPTOR)
        field(MESSAGE_ALT3_DESCRIPTOR)
    }

    public fun deserialize(headers: Headers, payload: ByteArray?): ErrorDetails {
        var message = headers[X_AMZN_ERROR_MESSAGE_HEADER_NAME] ?: headers[X_AMZN_EVENT_ERROR_MESSAGE_HEADER_NAME]
        val headerCode: String? = headers[X_AMZN_ERROR_TYPE_HEADER_NAME]
        var bodyCode: String? = null
        var bodyType: String? = null

        if (payload != null) {
            JsonDeserializer(payload).deserializeStruct(OBJ_DESCRIPTOR) {
                loop@ while (true) {
                    when (findNextFieldIndex()) {
                        ERR_CODE_ALT1_DESCRIPTOR.index -> bodyCode = deserializeString()
                        ERR_CODE_ALT2_DESCRIPTOR.index -> bodyType = deserializeString()
                        MESSAGE_ALT1_DESCRIPTOR.index,
                        MESSAGE_ALT2_DESCRIPTOR.index,
                        MESSAGE_ALT3_DESCRIPTOR.index,
                        -> message = deserializeString()
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        }
        /**
         * According to the spec we should check the following locations in order:
         *
         * HTTP header x-amzn-errortype
         * top-level body field code
         * top-level body field __type
         *
         * Source: https://github.com/awslabs/aws-sdk-kotlin/issues/828
         */
        return ErrorDetails(sanitize(headerCode ?: bodyCode ?: bodyType), message, requestId = null)
    }
}

/**
 * Sanitize the value to retrieve the disambiguated error type using the following steps:
 *
 * If a : character is present, then take only the contents before the first : character in the value.
 * If a # character is present, then take only the contents after the first # character in the value.
 *
 * All of the following error values resolve to FooError:
 *
 * FooError
 * FooError:http://amazon.com/smithy/com.amazon.smithy.validate/
 * aws.protocoltests.restjson#FooError
 * aws.protocoltests.restjson#FooError:http://amazon.com/smithy/com.amazon.smithy.validate/
 */
private fun sanitize(code: String?): String? = code?.substringAfter("#")?.substringBefore(":")
