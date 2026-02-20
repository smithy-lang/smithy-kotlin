/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.sdk.kotlin.crt.CrtRuntimeException
import aws.sdk.kotlin.crt.util.hashing.eccKeyPairFromPrivateKey
import aws.sdk.kotlin.crt.util.hashing.releaseEccKeyPair
import aws.sdk.kotlin.crt.util.hashing.signMessage
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * ECDSA on the SECP256R1 curve.
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun ecdsaSecp256r1(key: ByteArray, message: ByteArray): ByteArray {
    val keyPair = eccKeyPairFromPrivateKey(key) ?: throw CrtRuntimeException("Failed to create ECC key pair from private key")
    try {
        return signMessage(keyPair, message)
    } finally {
        releaseEccKeyPair(keyPair)
    }
}
