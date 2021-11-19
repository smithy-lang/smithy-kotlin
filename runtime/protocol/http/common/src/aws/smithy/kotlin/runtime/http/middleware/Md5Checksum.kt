/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.http.Feature
import aws.smithy.kotlin.runtime.http.FeatureKey
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpClientFeatureFactory
import aws.smithy.kotlin.runtime.http.operation.SdkHttpOperation
import aws.smithy.kotlin.runtime.http.request.header
import aws.smithy.kotlin.runtime.util.InternalApi
import aws.smithy.kotlin.runtime.util.encodeBase64String
import aws.smithy.kotlin.runtime.util.md5

/**
 * Set the `Content-MD5` header based on the current payload
 * See:
 *   - https://awslabs.github.io/smithy/1.0/spec/core/behavior-traits.html#httpchecksumrequired-trait
 *   - https://datatracker.ietf.org/doc/html/rfc1864.html
 */
@InternalApi
class Md5Checksum : Feature {

    companion object Feature : HttpClientFeatureFactory<Md5Checksum, Md5Checksum> {
        override val key: FeatureKey<Md5Checksum> = FeatureKey("Md5Checksum")
        override fun create(block: Md5Checksum.() -> Unit): Md5Checksum =
            Md5Checksum().apply(block)
    }

    override fun <I, O> install(operation: SdkHttpOperation<I, O>) {
        operation.execution.mutate.intercept { req, next ->
            val checksum = when (val body = req.subject.body) {
                is HttpBody.Bytes -> body.bytes().md5().encodeBase64String()
                else -> null
            }

            checksum?.let { req.subject.header("Content-MD5", it) }
            next.call(req)
        }
    }
}
