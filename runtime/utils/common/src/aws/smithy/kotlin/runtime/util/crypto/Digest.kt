/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.util.crypto

import aws.smithy.kotlin.runtime.util.encodeToHex

interface Digest {
    fun append(input: ByteArray)
    fun compute(): ByteArray
    fun reset()
}

fun Digest.append(input: String) = append(input.encodeToByteArray())

fun Digest.compute(input: ByteArray): ByteArray {
    reset()
    append(input)
    return compute()
}

fun Digest.compute(input: String): ByteArray {
    reset()
    append(input)
    return compute()
}

fun Digest.computeAsHex(): String = compute().encodeToHex()

fun Digest.computeAsHex(input: ByteArray): String = compute(input).encodeToHex()

fun Digest.computeAsHex(input: String): String = compute(input).encodeToHex()
