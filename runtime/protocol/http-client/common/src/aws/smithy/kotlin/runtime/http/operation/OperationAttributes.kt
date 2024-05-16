/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.AttributeKey

@InternalApi
public object OperationAttributes {
    public val RpcService: AttributeKey<String> = AttributeKey("rpc.service")
    public val RpcOperation: AttributeKey<String> = AttributeKey("rpc.operation")
    public val AwsInvocationId: AttributeKey<String> = AttributeKey("aws.invocation_id")
}
