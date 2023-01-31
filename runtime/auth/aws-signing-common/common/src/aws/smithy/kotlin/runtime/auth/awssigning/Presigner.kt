/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.endpoints.Endpoint
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import kotlin.time.Duration

// Note: the following types are essentially smithy-kotlin local versions of the following AWS types:
// * SigningContext ≈ CredentialScope
// * SigningContextualizedEndpoint ≈ AwsEndpoint
// * SigningEndpointProvider ≈ AwsEndpointResolver
// Rather than move those AWS-specific types down into smithy-kotlin (where there's no good home for them) we reproduce
// them here for presigning.

/**
 * Represents the context under which signing takes place. These parameters are used in the calculation of a valid
 * signature.
 * @param service The service for which the API is being signed. If none is specified then the service in
 * [PresignedRequestConfig] is used instead.
 * @param region The region in which the API call would occur. If none is specified then the region in
 * [PresignedRequestConfig] is used instead.
 */
public data class SigningContext(public val service: String?, public val region: String?)

/**
 * Represents a endpoint that will be used for signing which has optionally been contextualized with additional signing
 * overrides.
 * @param endpoint The endpoint for the API call which will be signed.
 * @param context The [SigningContext] overrides for signing. If none are specified, the values in
 * [PresignedRequestConfig] are used instead.
 */
public data class SigningContextualizedEndpoint(public val endpoint: Endpoint, public val context: SigningContext?)

/**
 * A lambda function that returns an endpoint and optional signing config overrides based on the given service/region.
 */
public typealias SigningEndpointProvider = suspend (SigningContext) -> SigningContextualizedEndpoint

/**
 * The service configuration details for a presigned request
 *
 * @property region The AWS region to which the request is going
 * @property signingName The signing name used to sign the request
 * @property serviceId the service id used to sign the request
 * @property endpointProvider Resolves the contextualized endpoint to determine where the request should be sent
 * @property credentialsProvider Resolves credentials to sign the request with
 * @property useDoubleUriEncode Determines if presigner should double encode Uri
 * @property normalizeUriPath Determines if presigned URI path will be normalized
 */
public interface ServicePresignConfig {
    public val signer: AwsSigner
    public val region: String
    public val signingName: String
    public val serviceId: String
    public val endpointProvider: SigningEndpointProvider
    public val credentialsProvider: CredentialsProvider
    public val useDoubleUriEncode: Boolean
    public val normalizeUriPath: Boolean
}

/**
 * Where the signature is placed in the presigned request
 * @property HEADER
 * @property QUERY_STRING
 */
public enum class PresigningLocation {
    /**
     * Signing details are to be placed in a header
     */
    HEADER,

    /**
     * Signing details to be added to the query string
     */
    QUERY_STRING,
}

/**
 * Configuration of a presigned request
 * @property method HTTP method of the presigned request
 * @property path HTTP path of the presigned request
 * @property queryString the HTTP querystring of the presigned request
 * @property expiresAfter Amount of time that the request will be valid for after being signed
 * @property signBody Specifies if the request body should be signed
 * @property presigningLocation Specifies where the signing information should be placed in the presigned request
 * @property additionalHeaders Custom headers that should be signed as part of the request
 */
public data class PresignedRequestConfig(
    public val method: HttpMethod,
    public val path: String,
    public val queryString: QueryParameters = QueryParameters.Empty,
    public val expiresAfter: Duration,
    public val signBody: Boolean = false,
    public val presigningLocation: PresigningLocation,
    public val additionalHeaders: Headers = Headers.Empty,
)

/**
 * Generate a presigned request given the service and operation configurations
 * @param serviceConfig The service configuration to use in signing the request
 * @param requestConfig The presign configuration to use in signing the request
 * @return a [HttpRequest] that can be executed by any HTTP client within the specified duration
 */
@InternalApi
public suspend fun createPresignedRequest(
    serviceConfig: ServicePresignConfig,
    requestConfig: PresignedRequestConfig,
): HttpRequest {
    val givenSigningContext = SigningContext(serviceConfig.serviceId, serviceConfig.region)
    val endpoint = serviceConfig.endpointProvider(givenSigningContext)
    val signatureType = when (requestConfig.presigningLocation) {
        PresigningLocation.HEADER -> AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
        PresigningLocation.QUERY_STRING -> AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
    }
    val hashSpecification =
        if (requestConfig.signBody) HashSpecification.CalculateFromPayload else HashSpecification.UnsignedPayload

    val signingConfig = AwsSigningConfig {
        region = endpoint.context?.region ?: serviceConfig.region
        service = endpoint.context?.service ?: serviceConfig.signingName
        credentialsProvider = serviceConfig.credentialsProvider
        this.signatureType = signatureType
        signedBodyHeader = AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256
        this.hashSpecification = hashSpecification
        expiresAfter = requestConfig.expiresAfter
        useDoubleUriEncode = serviceConfig.useDoubleUriEncode
        normalizeUriPath = serviceConfig.normalizeUriPath
    }

    val unsignedUrl = Url(
        scheme = Protocol.HTTPS,
        host = endpoint.endpoint.uri.host,
        port = endpoint.endpoint.uri.port,
        path = requestConfig.path,
        parameters = requestConfig.queryString,
    )

    val request = HttpRequest(
        requestConfig.method,
        unsignedUrl,
        Headers {
            append("Host", endpoint.endpoint.uri.host.toString())
            appendAll(requestConfig.additionalHeaders)
        },
        HttpBody.Empty,
    )
    val result = serviceConfig.signer.sign(request, signingConfig)
    val signedRequest = result.output

    return HttpRequest(
        method = signedRequest.method,
        url = Url(
            scheme = Protocol.HTTPS,
            host = endpoint.endpoint.uri.host,
            port = endpoint.endpoint.uri.port,
            path = signedRequest.url.path,
            parameters = signedRequest.url.parameters,
            encodeParameters = false,
        ),
        headers = signedRequest.headers,
        body = HttpBody.Empty,
    )
}
