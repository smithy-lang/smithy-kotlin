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

/**
 * Represents an HTTP verb
 */
enum class HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS;

    companion object {
        /**
         * Parse from a raw string representation of an HTTP method (e.g. "get")
         * @return [HttpMethod] for the given string
         * @throws IllegalArgumentException if the method is unknown
         */
        fun parse(method: String): HttpMethod {
            return when (method.toUpperCase()) {
                GET.name -> GET
                POST.name -> POST
                PUT.name -> PUT
                PATCH.name -> PATCH
                DELETE.name -> DELETE
                HEAD.name -> HEAD
                OPTIONS.name -> OPTIONS
                else -> throw IllegalArgumentException("unknown HTTP method: $method")
            }
        }
    }
}
