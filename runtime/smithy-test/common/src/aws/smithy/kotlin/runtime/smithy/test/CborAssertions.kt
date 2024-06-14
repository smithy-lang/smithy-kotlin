/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.serde.cbor.CborDeserializer

/**
 * Asserts two HTTP bodies are equal as application/cbor documents
 */
public suspend fun assertCborBodiesEqual(expected: HttpBody?, actual: HttpBody?) {
    val expectedBytes = expected?.readAll()
    val actualBytes = actual?.readAll()
    if (expectedBytes == null && actualBytes == null) {
        return
    }

    requireNotNull(expectedBytes) { "expected application/cbor body cannot be null" }
    requireNotNull(actualBytes) { "actual application/cbor body cannot be null" }

    val expectedDeserializer = CborDeserializer(expectedBytes)
    val actualDeserializer = CborDeserializer(actualBytes)
}
