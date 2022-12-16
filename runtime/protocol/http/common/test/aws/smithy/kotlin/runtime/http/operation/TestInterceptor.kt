/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.client.RequestInterceptorContext
import aws.smithy.kotlin.runtime.client.ResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse

class TestException(override val message: String?) : IllegalStateException()

open class TestInterceptor(
    val id: String,
    val hooksFired: MutableList<String> = mutableListOf<String>(),
    val failOnHooks: Set<String> = emptySet(),
) : HttpInterceptor {

    private fun trace(hook: String) {
        hooksFired.add("$id:$hook")
        if (hook in failOnHooks) {
            throw TestException("interceptor $id failed on $hook")
        }
    }

    override fun readBeforeExecution(context: RequestInterceptorContext<Any>) {
        trace("readBeforeExecution")
    }

    override fun modifyBeforeSerialization(context: RequestInterceptorContext<Any>): Any {
        trace("modifyBeforeSerialization")
        return super.modifyBeforeSerialization(context)
    }

    override fun readBeforeSerialization(context: RequestInterceptorContext<Any>) {
        trace("readBeforeSerialization")
        super.readBeforeSerialization(context)
    }

    override fun readAfterSerialization(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        trace("readAfterSerialization")
        super.readAfterSerialization(context)
    }

    override fun modifyBeforeRetryLoop(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        trace("modifyBeforeRetryLoop")
        return super.modifyBeforeRetryLoop(context)
    }

    override fun readBeforeAttempt(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        trace("readBeforeAttempt")
        super.readBeforeAttempt(context)
    }

    override fun modifyBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        trace("modifyBeforeSigning")
        return super.modifyBeforeSigning(context)
    }

    override fun readBeforeSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        trace("readBeforeSigning")
        super.readBeforeSigning(context)
    }

    override fun readAfterSigning(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        trace("readAfterSigning")
        super.readAfterSigning(context)
    }

    override fun modifyBeforeTransmit(context: ProtocolRequestInterceptorContext<Any, HttpRequest>): HttpRequest {
        trace("modifyBeforeTransmit")
        return super.modifyBeforeTransmit(context)
    }

    override fun readBeforeTransmit(context: ProtocolRequestInterceptorContext<Any, HttpRequest>) {
        trace("readBeforeTransmit")
        super.readBeforeTransmit(context)
    }

    override fun readAfterTransmit(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
        trace("readAfterTransmit")
        super.readAfterTransmit(context)
    }

    override fun modifyBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>): HttpResponse {
        trace("modifyBeforeDeserialization")
        return super.modifyBeforeDeserialization(context)
    }

    override fun readBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
        trace("readBeforeDeserialization")
        super.readBeforeDeserialization(context)
    }

    override fun readAfterDeserialization(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse>) {
        trace("readAfterDeserialization")
        super.readAfterDeserialization(context)
    }

    override fun modifyBeforeAttemptCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>): Result<Any> {
        trace("modifyBeforeAttemptCompletion")
        return super.modifyBeforeAttemptCompletion(context)
    }

    override fun readAfterAttempt(context: ResponseInterceptorContext<Any, Any, HttpRequest, HttpResponse?>) {
        trace("readAfterAttempt")
        super.readAfterAttempt(context)
    }

    override fun modifyBeforeCompletion(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>): Result<Any> {
        trace("modifyBeforeCompletion")
        return super.modifyBeforeCompletion(context)
    }

    override fun readAfterExecution(context: ResponseInterceptorContext<Any, Any, HttpRequest?, HttpResponse?>) {
        trace("readAfterExecution")
        super.readAfterExecution(context)
    }
}
