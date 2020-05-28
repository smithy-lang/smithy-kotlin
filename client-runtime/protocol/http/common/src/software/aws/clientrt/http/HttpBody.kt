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
package software.aws.clientrt.http

import software.aws.clientrt.io.Source

/**
 * HTTP payload to be sent to a peer
 */
sealed class HttpBody {

    /**
     * Specifies the length of this [HttpBody] content
     * If null it is assumed to be a streaming source using e.g. `Transfer-Encoding: Chunked`
     */
    open val contentLength: Long? = null

    /**
     * Variant of a [HttpBody] without a payload
     */
    object Empty : HttpBody() {
        override val contentLength: Long? = 0
    }

    /**
     * Variant of a [HttpBody] with payload represented as [ByteArray]
     *
     * Useful for content that can be fully realized in memory (e.g. most text/JSON payloads)
     */
    abstract class Bytes : HttpBody() {
        /**
         * Provides [ByteArray] to be sent to peer
         */
        abstract fun bytes(): ByteArray
    }

    /**
     * Variant of an [HttpBody] with a streaming payload. Content is read from the given source
     */
    abstract class Streaming : HttpBody() {
        /**
         * Provides [Source] for the content
         */
        abstract fun readFrom(): Source
    }
}
