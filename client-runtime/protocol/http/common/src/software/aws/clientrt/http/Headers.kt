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

/**
 * Immutable mapping of case insensitive HTTP header names to list of [String] values.
 */
interface Headers : StringValuesMap {
    companion object {
        operator fun invoke(block: HeadersBuilder.() -> Unit): Headers = HeadersBuilder()
            .apply(block).build()
    }
}

/**
 * Build an immutable HTTP header map
 */
class HeadersBuilder : StringValuesMapBuilder(true, 8) {
    override fun build(): Headers {
        require(!built) { "HeadersBuilder can only build a single Headers instance" }
        built = true
        return HeadersImpl(values)
    }
}

private class HeadersImpl(
    values: Map<String, List<String>>
) : Headers, StringValuesMapImpl(true, values) {
    override fun toString(): String = "Headers ${entries()}"
}
