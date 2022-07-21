/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * HTTP middleware feature that allows mutation of in-flight request headers
 */
@InternalApi
public class MutateHeaders(
    override: Map<String, String> = emptyMap(),
    append: Map<String, String> = emptyMap(),
    setMissing: Map<String, String> = emptyMap(),
) : ModifyRequestMiddleware {
    private val overrides = HeadersBuilder()
    private val additional = HeadersBuilder()
    private val conditionallySet = HeadersBuilder()

    init {
        override.forEach { (key, value) -> set(key, value) }
        append.forEach { (key, value) -> append(key, value) }
        setMissing.forEach { (key, value) -> setIfMissing(key, value) }
    }

    /**
     * Set a header in the request, overriding any existing key of the same name
     */
    public fun set(name: String, value: String): Unit = overrides.set(name, value)

    /**
     * Append a value to headers in the request that may already be set, setting the value
     * if the key doesn't already exist
     */
    public fun append(name: String, value: String): Unit = additional.append(name, value)

    /**
     * Set a header with the given [name] only if the request does not already have a header
     * set for the same name.
     */
    public fun setIfMissing(name: String, value: String): Unit = conditionallySet.append(name, value)

    override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest {
        additional.entries().forEach { (key, values) ->
            req.subject.headers.appendAll(key, values)
        }

        overrides.entries().forEach { (key, values) ->
            req.subject.headers[key] = values.last()
        }

        conditionallySet.entries().forEach { (key, values) ->
            if (!req.subject.headers.contains(key)) {
                req.subject.headers[key] = values.last()
            }
        }

        return req
    }
}
