/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde

interface SdkSerializable {
    fun serialize(serializer: Serializer)
}

// FIXME - baby steps
// Glue code for marrying raw serialize functions to SdkSerializable

typealias SerializeFn<T> = (serializer: Serializer, input: T) -> Unit

fun <T> StructSerializer.field(descriptor: SdkFieldDescriptor, input: T, serializeFn: SerializeFn<T>) {
    field(descriptor, SdkSerializableLambda(input, serializeFn))
}

private data class SdkSerializableLambda<T>(
    private val input: T,
    private val serializeFn: SerializeFn<T>
) : SdkSerializable {
    override fun serialize(serializer: Serializer) {
        serializeFn(serializer, input)
    }
}

fun <T> asSdkSerializable(input: T, serializeFn: SerializeFn<T>): SdkSerializable = SdkSerializableLambda(input, serializeFn)
