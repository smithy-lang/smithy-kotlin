/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing

/**
 * ECDSA on the SECP256R1 curve.
 */
public expect fun ecdsasecp256r1(key: ByteArray, message: ByteArray): ByteArray