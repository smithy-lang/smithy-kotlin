/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.sdk.kotlin.crt.util.hashing.destroyKeyPair
import aws.sdk.kotlin.crt.util.hashing.keyPairFromPrivateKey
import aws.sdk.kotlin.crt.util.hashing.signMessage
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * ECDSA on the SECP256R1 curve.
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun ecdsaSecp256r1(key: ByteArray, message: ByteArray): ByteArray {
    val keyPair = keyPairFromPrivateKey(key) ?: error("Failed to create key pair")
    try {
        return signMessage(keyPair, message)
    } finally {
        destroyKeyPair(keyPair)
    }
}
