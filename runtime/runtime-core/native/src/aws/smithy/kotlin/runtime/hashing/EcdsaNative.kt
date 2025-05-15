/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

// FIXME Implement using aws-c-cal: https://github.com/awslabs/aws-c-cal/blob/main/include/aws/cal/ecc.h
// Will need to be implemented and exposed in aws-crt-kotlin. Or maybe we can _only_ offer the CRT signer on Native?
// Will require updating DefaultAwsSigner to be expect/actual and set to CrtSigner on Native.
/**
 * ECDSA on the SECP256R1 curve.
 */
public actual fun ecdsaSecp256r1(key: ByteArray, message: ByteArray): ByteArray = TODO("Not yet implemented")
