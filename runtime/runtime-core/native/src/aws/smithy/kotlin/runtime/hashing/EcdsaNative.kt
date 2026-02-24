/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.sdk.kotlin.crt.util.hashing.EcdsaSecp256r1Native
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * ECDSA on the SECP256R1 curve.
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun ecdsaSecp256r1(key: ByteArray, message: ByteArray): ByteArray {
    val ecdsa = EcdsaSecp256r1Native(key)
    try {
        return ecdsa.signMessage(message)
    } finally {
        ecdsa.releaseMemory()
    }
}
