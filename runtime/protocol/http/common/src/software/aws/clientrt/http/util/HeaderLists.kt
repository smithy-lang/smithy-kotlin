/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
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
