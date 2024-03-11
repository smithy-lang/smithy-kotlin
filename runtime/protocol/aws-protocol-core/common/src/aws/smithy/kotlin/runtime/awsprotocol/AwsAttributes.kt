/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.awsprotocol

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.AttributeKey

@InternalApi
public object AwsAttributes {
    /**
     * The AWS region the client should use. Note this is not always the same as [AwsSigningAttributes.SigningRegion] in
     * the case of global services like IAM
     */
    public val Region: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#AwsRegion")
}
