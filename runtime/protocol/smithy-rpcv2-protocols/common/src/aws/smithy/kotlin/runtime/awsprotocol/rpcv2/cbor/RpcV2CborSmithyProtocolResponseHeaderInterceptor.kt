/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol.rpcv2.cbor

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse

@InternalApi
public object RpcV2CborSmithyProtocolResponseHeaderInterceptor : HttpInterceptor {
    override fun readBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
        val response = context.protocolResponse
        val smithyProtocolHeader = response.headers["smithy-protocol"]

        if (smithyProtocolHeader != "rpc-v2-cbor") {
            throw ClientException("Expected `smithy-protocol` response header `rpc-v2-cbor`, got `$smithyProtocolHeader`")
        }
    }
}