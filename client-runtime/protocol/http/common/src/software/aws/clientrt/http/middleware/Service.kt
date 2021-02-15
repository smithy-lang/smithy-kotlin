/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.middleware

/**
 * Service takes a [Request] and produces a [Response]
 */
interface Service<in Request, out Response> {
    suspend fun call(request: Request): Response
}

/**
 * Alias for a lambda based [Service]
 */
typealias ServiceFn<Request, Response> = suspend (Request) -> Response

/**
 * Adapter for [ServiceFn] that implements [Service]
 */
data class ServiceLambda<Request, Response>(private val fn: ServiceFn<Request, Response>) : Service<Request, Response> {
    override suspend fun call(request: Request): Response {
        return fn(request)
    }
}
