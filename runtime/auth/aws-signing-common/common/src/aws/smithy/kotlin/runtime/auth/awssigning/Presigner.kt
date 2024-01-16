/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.client.endpoints.authOptions
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.operation.EndpointResolver
import aws.smithy.kotlin.runtime.http.operation.ResolveEndpointRequest
import aws.smithy.kotlin.runtime.http.operation.setResolvedEndpoint
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.operation.ExecutionContext

@InternalApi
public suspend fun presignRequest(
    unsignedRequestBuilder: HttpRequestBuilder,
    ctx: ExecutionContext,
    credentialsProvider: CredentialsProvider,
    endpointResolver: EndpointResolver,
    signer: AwsSigner,
    signingConfig: AwsSigningConfig.Builder.() -> Unit,
): HttpRequest {
    unsignedRequestBuilder.body = HttpBody.Empty

    val credentials = credentialsProvider.resolve()
    val eprRequest = ResolveEndpointRequest(ctx, unsignedRequestBuilder.build(), credentials)
    val endpoint = endpointResolver.resolve(eprRequest)
    setResolvedEndpoint(unsignedRequestBuilder, ctx, endpoint)

    val signingContext = endpoint.authOptions.firstOrNull { it.schemeId == AuthSchemeId.AwsSigV4 }?.attributes ?: emptyAttributes()

    val config = AwsSigningConfig {
        region = signingContext.getOrNull(AwsSigningAttributes.SigningRegion)
        service = signingContext.getOrNull(AwsSigningAttributes.SigningService)
        this.credentials = credentials
        signedBodyHeader = AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256
        signatureType = AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
        hashSpecification = HashSpecification.UnsignedPayload // By default (should override for awsQuery)

        // Apply service/caller-specific overrides
        signingConfig()
    }

    val result = signer.sign(unsignedRequestBuilder.build(), config)
    val signedRequest = result.output

    return HttpRequest(
        method = signedRequest.method,
        url = Url {
            scheme = endpoint.uri.scheme
            host = endpoint.uri.host
            port = endpoint.uri.port
            path.copyFrom(signedRequest.url.path)
            parameters.copyFrom(signedRequest.url.parameters)
        },
        headers = signedRequest.headers,
        body = HttpBody.Empty,
    )
}
