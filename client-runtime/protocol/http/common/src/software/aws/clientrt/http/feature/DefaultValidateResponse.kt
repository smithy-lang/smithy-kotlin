/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.feature

import software.aws.clientrt.SdkBaseException
import software.aws.clientrt.http.*
import software.aws.clientrt.http.request.HttpRequest

/**
 * Generic HTTP service exception
 */
class HttpResponseException : SdkBaseException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    /**
     * The HTTP response status code
     */
    var statusCode: HttpStatusCode? = null

    /**
     * The response headers
     */
    var headers: Headers? = null

    /**
     * The response payload, if available
     */
    var body: ByteArray? = null

    /**
     * The original request
     */
    var request: HttpRequest? = null
}

/**
 * Feature that inspects the HTTP response and throws an exception if it is not successful
 * This is provided for clients generated by smithy-kotlin-codegen. Not expected to be used by AWS
 * services which define specific mappings from an error to the appropriate modeled exception. Out of the
 * box nothing in Smithy gives us that ability (other than the HTTP status code which is not guaranteed unique per error)
 * so all we can do is throw a generic exception with the code and let the user figure out what modeled error it was
 * using whatever matching mechanism they want.
 */
class DefaultValidateResponse : Feature {
    companion object Feature : HttpClientFeatureFactory<DefaultValidateResponse, DefaultValidateResponse> {
        override val key: FeatureKey<DefaultValidateResponse> = FeatureKey("DefaultValidateResponse")
        override fun create(block: DefaultValidateResponse.() -> Unit): DefaultValidateResponse {
            return DefaultValidateResponse().apply(block)
        }
    }

    override fun install(client: SdkHttpClient) {
//        client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
//            if (context.response.status.isSuccess()) {
//                proceed()
//                return@intercept
//            }
//
//            val message = "received unsuccessful HTTP response: ${context.response.status}"
//            val httpException = HttpResponseException(message).apply {
//                statusCode = context.response.status
//                headers = context.response.headers
//                body = context.response.body.readAll()
//                request = context.response.request
//            }
//
//            throw httpException
//        }
    }
}
