/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime

import aws.smithy.kotlin.runtime.collections.MutableAttributes
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ERROR_CODE = "ErrorWithNoMessage"
private const val METADATA_MESSAGE = "This is a message included in metadata but not the regular response"
private const val PROTOCOL_RESPONSE_SUMMARY = "HTTP 418 I'm a teapot"
private const val REQUEST_ID = "abcd-1234"
private const val SERVICE_MESSAGE = "This is an service-provided message"

private val ERROR_TYPE = ServiceException.ErrorType.Server
private val PROTOCOL_RESPONSE = object : ProtocolResponse {
    override val summary: String = PROTOCOL_RESPONSE_SUMMARY
}

class ExceptionsTest {
    @Test
    fun testRegularMessage() {
        val e = FooServiceException(SERVICE_MESSAGE)
        assertEquals(SERVICE_MESSAGE, e.message)
    }

    @Test
    fun testMetadataMessage() {
        val e = FooServiceException {
            set(ServiceErrorMetadata.ErrorMessage, METADATA_MESSAGE)
        }
        assertEquals(METADATA_MESSAGE, e.message)
    }

    @Test
    fun testRegularMessageWithRequestId() {
        val e = FooServiceException(SERVICE_MESSAGE) {
            set(ServiceErrorMetadata.RequestId, REQUEST_ID)
        }
        assertEquals("$SERVICE_MESSAGE, Request ID: $REQUEST_ID", e.message)
    }

    @Test
    fun testMetadataMessageWithRequestId() {
        val e = FooServiceException {
            set(ServiceErrorMetadata.ErrorMessage, METADATA_MESSAGE)
            set(ServiceErrorMetadata.RequestId, REQUEST_ID)
        }
        assertEquals("$METADATA_MESSAGE, Request ID: $REQUEST_ID", e.message)
    }

    @Test
    fun testErrorCodeNoMessage() {
        val e = FooServiceException {
            set(ServiceErrorMetadata.ErrorCode, ERROR_CODE)
            set(ServiceErrorMetadata.ErrorType, ERROR_TYPE)
            set(ServiceErrorMetadata.ProtocolResponse, PROTOCOL_RESPONSE)
        }
        assertEquals(
            "Service returned error code $ERROR_CODE, " +
                "Error type: $ERROR_TYPE, " +
                "Protocol response: $PROTOCOL_RESPONSE_SUMMARY",
            e.message,
        )
    }

    @Test
    fun testErrorCodeNoMessageWithRequestId() {
        val e = FooServiceException {
            set(ServiceErrorMetadata.ErrorCode, ERROR_CODE)
            set(ServiceErrorMetadata.ErrorType, ERROR_TYPE)
            set(ServiceErrorMetadata.ProtocolResponse, PROTOCOL_RESPONSE)
            set(ServiceErrorMetadata.RequestId, REQUEST_ID)
        }
        assertEquals(
            "Service returned error code $ERROR_CODE, " +
                "Error type: $ERROR_TYPE, " +
                "Protocol response: $PROTOCOL_RESPONSE_SUMMARY, " +
                "Request ID: $REQUEST_ID",
            e.message,
        )
    }

    @Test
    fun testNoErrorCodeNoMessage() {
        val e = FooServiceException {
            set(ServiceErrorMetadata.ErrorType, ERROR_TYPE)
            set(ServiceErrorMetadata.ProtocolResponse, PROTOCOL_RESPONSE)
        }
        assertEquals("Error type: $ERROR_TYPE, Protocol response: $PROTOCOL_RESPONSE_SUMMARY", e.message)
    }

    @Test
    fun testNoErrorCodeNoMessageWithRequestId() {
        val e = FooServiceException {
            set(ServiceErrorMetadata.ErrorType, ERROR_TYPE)
            set(ServiceErrorMetadata.ProtocolResponse, PROTOCOL_RESPONSE)
            set(ServiceErrorMetadata.RequestId, REQUEST_ID)
        }
        assertEquals(
            "Error type: $ERROR_TYPE, Protocol response: $PROTOCOL_RESPONSE_SUMMARY, Request ID: $REQUEST_ID",
            e.message,
        )
    }
}

private class FooServiceException(
    message: String? = null,
    attributes: MutableAttributes.() -> Unit = { },
) : ServiceException(message) {
    init {
        sdkErrorMetadata.attributes.apply(attributes)
    }
}
