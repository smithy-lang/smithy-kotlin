/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol.xml

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.awsprotocol.ErrorDetails
import aws.smithy.kotlin.runtime.serde.getOrDeserializeErr
import aws.smithy.kotlin.runtime.serde.xml.*

internal data class Ec2QueryErrorResponse(val errors: List<Ec2QueryError>, val requestId: String?)

internal data class Ec2QueryError(val code: String?, val message: String?)

@InternalApi
public fun parseEc2QueryErrorResponse(payload: ByteArray): ErrorDetails {
    val response = Ec2QueryErrorResponseDeserializer.deserialize(xmlStreamReader(payload).root())
    val firstError = response.errors.firstOrNull()
    return ErrorDetails(firstError?.code, firstError?.message, response.requestId)
}

/**
 * Deserializes EC2 Query protocol errors as specified by
 * https://smithy.io/2.0/aws/protocols/aws-ec2-query-protocol.html#operation-error-serialization
 */
internal object Ec2QueryErrorResponseDeserializer {
    fun deserialize(root: TagReader): Ec2QueryErrorResponse = runCatching {
        var errors: List<Ec2QueryError>? = null
        var requestId: String? = null
        if (root.startTag.name.tag != "Response") error("expected <Response> found ${root.startTag}")

        loop@while (true) {
            val curr = root.nextTag() ?: break@loop
            when (curr.startTag.name.tag) {
                "Errors" -> errors = Ec2QueryErrorListDeserializer.deserialize(curr)
                "RequestId" -> requestId = curr.data()
            }
            curr.drop()
        }

        Ec2QueryErrorResponse(errors ?: emptyList(), requestId)
    }.getOrDeserializeErr { "Unable to deserialize EC2Query error" }
}

internal object Ec2QueryErrorListDeserializer {
    fun deserialize(root: TagReader): List<Ec2QueryError> {
        val errors = mutableListOf<Ec2QueryError>()
        loop@ while (true) {
            val curr = root.nextTag() ?: break@loop
            when (curr.startTag.name.tag) {
                "Error" -> {
                    val el = Ec2QueryErrorDeserializer.deserialize(curr)
                    errors.add(el)
                }
            }
            curr.drop()
        }
        return errors
    }
}

internal object Ec2QueryErrorDeserializer {

    fun deserialize(root: TagReader): Ec2QueryError {
        var code: String? = null
        var message: String? = null

        loop@ while (true) {
            val curr = root.nextTag() ?: break@loop
            when (curr.startTag.name.tag) {
                "Code" -> code = curr.data()
                "Message", "message" -> message = curr.data()
            }
            curr.drop()
        }
        return Ec2QueryError(code, message)
    }
}
