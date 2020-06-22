/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
package com.amazonaws.service.s3

import com.amazonaws.service.runtime.SdkClient
import com.amazonaws.service.s3.model.*


interface S3Client: SdkClient {
    override val serviceName: String
        get() = "s3"

    companion object {
        fun create(): S3Client = DefaultS3Client()
    }

    suspend fun putObject(input: PutObjectRequest): PutObjectResponse
    suspend fun putObject(block: PutObjectRequest.DslBuilder.() -> Unit): PutObjectResponse {
        val input = PutObjectRequest { block(this) }
        return putObject(input)
    }

    suspend fun getObjectAlt1(input: GetObjectRequest, block: suspend (GetObjectResponse) -> Unit)
    // NOTE: can't do a DSL builder for ALT-1, see notes in implementation/quip

    suspend fun getObjectAlt2(input: GetObjectRequest): GetObjectResponse
    suspend fun getObjectAlt2(block: GetObjectRequest.DslBuilder.() -> Unit): GetObjectResponse {
        val input = GetObjectRequest{ block(this) }
        return getObjectAlt2(input)
    }
}