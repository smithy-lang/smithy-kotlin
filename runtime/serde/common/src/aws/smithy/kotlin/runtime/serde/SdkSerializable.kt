/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde

public interface SdkSerializable {
    public fun serialize(serializer: Serializer)
}

// FIXME - baby steps
// Glue code for marrying raw serialize functions to SdkSerializable

public typealias SerializeFn<T> = (serializer: Serializer, input: T) -> Unit

public fun <T> StructSerializer.field(descriptor: SdkFieldDescriptor, input: T, serializeFn: SerializeFn<T>) {
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

// FIXME - this causes backing classes to be generated behind the scenes and contributes to the overall jar size
public fun <T> asSdkSerializable(input: T, serializeFn: SerializeFn<T>): SdkSerializable = SdkSerializableLambda(input, serializeFn)
