/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.signing.awssigning.common.middleware

import aws.smithy.kotlin.runtime.auth.signing.awssigning.common.AwsSigner
import aws.smithy.kotlin.runtime.auth.signing.awssigning.common.AwsSigningAttributes
import aws.smithy.kotlin.runtime.http.operation.InitializeMiddleware
import aws.smithy.kotlin.runtime.http.operation.OperationRequest
import aws.smithy.kotlin.runtime.io.Handler

class AwsSignerContextMiddleware<Request, Response>(val signer: AwsSigner) : InitializeMiddleware<Request, Response> {
    override suspend fun <H : Handler<OperationRequest<Request>, Response>> handle(
        request: OperationRequest<Request>,
        next: H,
    ): Response {
        request.context[AwsSigningAttributes.Signer] = signer
        return next.call(request)
    }
}
