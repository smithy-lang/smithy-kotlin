/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.http.middleware

import software.aws.clientrt.http.*
import software.aws.clientrt.http.operation.SdkHttpOperation

/**
 * HTTP middleware feature that allows mutation of in-flight request headers
 */
class MutateHeaders : Feature {
    private val overrides = HeadersBuilder()
    private val additional = HeadersBuilder()
    private val conditionallySet = HeadersBuilder()

    /**
     * Set a header in the request, overriding any existing key of the same name
     */
    fun set(name: String, value: String): Unit = overrides.set(name, value)

    /**
     * Append a value to headers in the request that may already be set, setting the value
     * if the key doesn't already exist
     */
    fun append(name: String, value: String): Unit = additional.append(name, value)

    /**
     * Set a header with the given [name] only if the request does not already have a header
     * set for the same name.
     */
    fun setIfMissing(name: String, value: String) = conditionallySet.append(name, value)

    companion object Feature : HttpClientFeatureFactory<MutateHeaders, MutateHeaders> {
        override val key: FeatureKey<MutateHeaders> = FeatureKey("AddHeaders")
        override fun create(block: MutateHeaders.() -> Unit): MutateHeaders =
            MutateHeaders().apply(block)
    }

    override fun <I, O> install(operation: SdkHttpOperation<I, O>) {
        operation.execution.mutate.intercept { req, next ->
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

            next.call(req)
        }
    }
}
