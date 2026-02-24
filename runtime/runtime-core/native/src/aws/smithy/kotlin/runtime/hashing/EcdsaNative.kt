/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

import aws.sdk.kotlin.crt.use
import aws.sdk.kotlin.crt.util.hashing.EcdsaSecp256r1Native
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * ECDSA on the SECP256R1 curve.
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun ecdsaSecp256r1(key: ByteArray, message: ByteArray): ByteArray =
    EcdsaSecp256r1Native(key).use { ecdsa ->
        ecdsa.signMessage(message)
    }
