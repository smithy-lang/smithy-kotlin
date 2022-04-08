/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.signing.awssigning.common

import aws.smithy.kotlin.runtime.auth.credentials.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.endpoints.AwsEndpointResolver
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.util.InternalApi
import kotlin.time.Duration

/**
 * The service configuration details for a presigned request
 *
 * @property region The AWS region to which the request is going
 * @property signingName The signing name used to sign the request
 * @property serviceId the service id used to sign the request
 * @property endpointResolver Resolves the endpoint to determine where the request should be sent
 * @property credentialsProvider Resolves credentials to sign the request with
 * @property useDoubleUriEncode Determines if presigner should double encode Uri
 * @property normalizeUriPath Determines if presigned URI path will be normalized
 */
interface ServicePresignConfig {
    val signer: AwsSigner
    val region: String
    val signingName: String
    val serviceId: String
    val endpointResolver: AwsEndpointResolver
    val credentialsProvider: CredentialsProvider
    val useDoubleUriEncode: Boolean
    val normalizeUriPath: Boolean
}

/**
 * Where the signature is placed in the presigned request
 * @property HEADER
 * @property QUERY_STRING
 */
enum class SigningLocation {
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
 * @property signingLocation Specifies where the signing information should be placed in the presigned request
 * @property additionalHeaders Custom headers that should be signed as part of the request
 */
data class PresignedRequestConfig(
    val method: HttpMethod,
    val path: String,
    val queryString: QueryParameters = QueryParameters.Empty,
    val expiresAfter: Duration,
    val signBody: Boolean = false,
    val signingLocation: SigningLocation,
    val additionalHeaders: Headers = Headers.Empty
)

/**
 * Generate a presigned request given the service and operation configurations
 * @param serviceConfig The service configuration to use in signing the request
 * @param requestConfig The presign configuration to use in signing the request
 * @return a [HttpRequest] that can be executed by any HTTP client within the specified duration
 */
@InternalApi
suspend fun createPresignedRequest(
    serviceConfig: ServicePresignConfig,
    requestConfig: PresignedRequestConfig,
): HttpRequest {
    val endpoint = serviceConfig.endpointResolver.resolve(serviceConfig.serviceId, serviceConfig.region)
    val signatureType = when (requestConfig.signingLocation) {
        SigningLocation.HEADER -> AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
        SigningLocation.QUERY_STRING -> AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
    }
    val bodyHashSource = if (requestConfig.signBody) {
        BodyHashSource.CalculateFromPayload
    } else {
        BodyHashSource.UnsignedPayload
    }

    val signingConfig = AwsSigningConfig {
        region = endpoint.credentialScope?.region ?: serviceConfig.region
        service = endpoint.credentialScope?.service ?: serviceConfig.signingName
        credentialsProvider = serviceConfig.credentialsProvider
        this.signatureType = signatureType
        signedBodyHeader = AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256
        this.bodyHashSource = bodyHashSource
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
            append("Host", endpoint.endpoint.uri.host)
            appendAll(requestConfig.additionalHeaders)
        },
        HttpBody.Empty,
    )
    val result = serviceConfig.signer.sign(request, signingConfig)
    val signedRequest = checkNotNull(result.signedRequest) { "Signing result must have a signed request" }

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
