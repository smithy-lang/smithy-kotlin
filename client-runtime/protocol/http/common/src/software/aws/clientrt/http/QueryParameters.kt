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

import software.aws.clientrt.http.util.StringValuesMap
import software.aws.clientrt.http.util.StringValuesMapBuilder
import software.aws.clientrt.http.util.StringValuesMapImpl
import software.aws.clientrt.http.util.urlEncodeComponent

/**
 * Container for HTTP query parameters
 */
interface QueryParameters : StringValuesMap {
    companion object {
        operator fun invoke(block: QueryParametersBuilder.() -> Unit): QueryParameters = QueryParametersBuilder()
            .apply(block).build()
    }
}

class QueryParametersBuilder : StringValuesMapBuilder(true, 8) {
    override fun build(): QueryParameters {
        require(!built) { "QueryParametersBuilder can only build a single instance" }
        built = true
        return QueryParametersImpl(values)
    }
}

private class QueryParametersImpl(values: Map<String, List<String>> = emptyMap()) : QueryParameters, StringValuesMapImpl(true, values) {
    override fun toString(): String = "QueryParameters ${entries()}"
}

/**
 * Return the encoded query parameter string (without leading '?')
 */
fun QueryParameters.urlEncode(): String = buildString {
    urlEncodeTo(this)
}

/**
 * URL encode the query parameters components to the appendable output (without the leading '?')
 */
fun QueryParameters.urlEncodeTo(out: Appendable) {
    entries().sortedBy { it.key }.forEachIndexed { i, entry ->
        entry.value.forEachIndexed { j, value ->
            if (i > 0 || j > 0) {
                out.append("&")
            }
            out.append(entry.key)
            out.append("=")
            out.append(value.urlEncodeComponent())
        }
    }
}
