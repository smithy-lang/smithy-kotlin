/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

/**
 * ECDSA on the SECP256R1 curve.
 */
public actual fun ecdsaSecp256r1(
    key: ByteArray,
    message: ByteArray,
    signatureType: EcdsaSignatureType,
): ByteArray = error("This function should not be invoked on Native, which uses the CrtAwsSigner.")
