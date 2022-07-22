/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.io

/**
 * Handler is an (asynchronous) transform from [Request] -> [Response]
 */
public interface Handler<in Request, out Response> {
    public suspend fun call(request: Request): Response
}

/**
 * Alias for a lambda based [Handler]
 */
public typealias HandlerFn<Request, Response> = suspend (Request) -> Response

/**
 * Adapter for [HandlerFn] that implements [Handler]
 */
public data class HandlerLambda<Request, Response>(private val fn: HandlerFn<Request, Response>) : Handler<Request, Response> {
    override suspend fun call(request: Request): Response = fn(request)
}
