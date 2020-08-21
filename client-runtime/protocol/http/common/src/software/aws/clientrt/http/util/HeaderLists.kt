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
package software.aws.clientrt.http.util

import software.aws.clientrt.ClientException

/**
 * Attempt to split an HTTP header [value] by commas and returns the resulting list
 */
fun splitHeaderListValues(value: String): List<String> = value.split(",").map { it.trim() }

/**
 * Attempt to split an HTTP header [value] as if it contains a list of HTTP-Date timestamp
 * values separated by commas. The split is aware of the HTTP-Date format and will skip
 * a comma within the timestamp value.
 */
fun splitHttpDateHeaderListValues(value: String): List<String> {
    val n = value.count { it == ',' }
    if (n <= 1) {
        return listOf(value)
    } else if (n % 2 == 0) {
        throw ClientException("invalid timestamp HttpDate header comma separations: `$value`")
    }

    var cnt = 0
    val splits = mutableListOf<String>()
    var startIdx = 0

    for (i in value.indices) {
        if (value[i] == ',') cnt++

        // split on every other ','
        if (cnt > 1) {
            splits.add(value.substring(startIdx, i).trim())
            startIdx = i + 1
            cnt = 0
        }
    }

    if (startIdx < value.length) {
        splits.add(value.substring(startIdx).trim())
    }

    return splits
}
