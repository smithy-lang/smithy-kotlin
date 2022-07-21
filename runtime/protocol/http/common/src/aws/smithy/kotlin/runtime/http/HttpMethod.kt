/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http

/**
 * Represents an HTTP verb
 */
public enum class HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS;

    public companion object {
        /**
         * Parse from a raw string representation of an HTTP method (e.g. "get")
         * @return [HttpMethod] for the given string
         * @throws IllegalArgumentException if the method is unknown
         */
        public fun parse(method: String): HttpMethod = when (method.uppercase()) {
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
