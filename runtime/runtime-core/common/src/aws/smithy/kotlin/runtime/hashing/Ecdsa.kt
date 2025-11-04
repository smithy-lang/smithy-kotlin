/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

public enum class EcdsaSignatureType {
    ASN1_DER,
    RAW_RS,
}

/**
 * ECDSA on the SECP256R1 curve.
 */
public expect fun ecdsaSecp256r1(
    key: ByteArray,
    message: ByteArray,
    signatureType: EcdsaSignatureType = EcdsaSignatureType.ASN1_DER,
): ByteArray
