/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.FieldTrait
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Major
import aws.smithy.kotlin.runtime.serde.expectTrait

/**
 * Specifies a CBOR name that a field is encoded into.
 */
@InternalApi
public data class CborSerialName(public val name: String) : FieldTrait {
    /**
     * The full CBOR encoding of this field name: the major-type-3 length header followed by the
     * UTF-8 bytes of the name. Field descriptors are singletons, so computing this once and
     * reusing the bytes avoids re-encoding the name (and allocating a [TextString] wrapper) on
     * every struct-field write.
     */
    internal val encoded: ByteArray by lazy {
        val bytes = name.encodeToByteArray()
        SdkBuffer().apply {
            writeArgument(Major.STRING, bytes.size.toULong())
            write(bytes)
        }.readByteArray()
    }
}

/**
 * Provides the serialized name of the field.
 */
@InternalApi
public val SdkFieldDescriptor.serialName: String
    get() = expectTrait<CborSerialName>().name

/**
 * Provides the pre-encoded CBOR bytes (length header + UTF-8 name) for the field's serialized name.
 */
internal val SdkFieldDescriptor.serialNameBytes: ByteArray
    get() = expectTrait<CborSerialName>().encoded
