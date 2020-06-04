/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.service.lambda.model

class InvokeRequest private constructor(builder: BuilderImpl){

    val functionName: String? = builder.functionName
    val invocationType: String? = builder.invocationType
    val logType: String? = builder.logType
    val clientContext: String? = builder.clientContext
    val payload: ByteArray? = builder.payload
    val qualifier: String? = builder.qualifier

    companion object {
        operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
    }

    interface Builder {
        fun build(): InvokeRequest
        // TODO - Java fill in Java builder
    }

    interface DslBuilder {
        // Location: PATH; Name: "FunctionName"
        var functionName: String?

        // Location: HEADER; Name: "X-Amz-Invocation-Type"
        var invocationType: String?

        // Location: HEADER; Name: "X-Amz-Log-Type"
        var logType: String?

        // Location: HEADER; Name: "X-Amz-Client-Context"
        var clientContext: String?

        // Location: PAYLOAD; Name: "Payload"
        var payload: ByteArray?

        // Location: QUERY; Name: "Qualifier"
        var qualifier: String?
    }

    private class BuilderImpl : Builder, DslBuilder {
        override var functionName: String? = null
        override var invocationType: String? = null
        override var logType: String? = null
        override var clientContext: String? = null
        override var payload: ByteArray? = null
        override var qualifier: String? = null
        override fun build(): InvokeRequest = InvokeRequest(this)
    }
}

