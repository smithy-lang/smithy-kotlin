/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

// Code generated by smithy-kotlin-codegen. DO NOT EDIT!

package aws.smithy.kotlin.serde.benchmarks.model.twitter

import aws.smithy.kotlin.runtime.serde.Deserializer
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.SdkObjectDescriptor
import aws.smithy.kotlin.runtime.serde.SerialKind
import aws.smithy.kotlin.runtime.serde.deserializeList
import aws.smithy.kotlin.runtime.serde.deserializeStruct
import aws.smithy.kotlin.runtime.serde.json.JsonSerialName


internal suspend fun deserializeHashtagDocument(deserializer: Deserializer): Hashtag {
    val builder = Hashtag.builder()
    val INDICES_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, JsonSerialName("indices"))
    val TEXT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("text"))
    val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        field(INDICES_DESCRIPTOR)
        field(TEXT_DESCRIPTOR)
    }

    deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
        loop@while (true) {
            when (findNextFieldIndex()) {
                INDICES_DESCRIPTOR.index -> builder.indices =
                    deserializer.deserializeList(INDICES_DESCRIPTOR) {
                        val col0 = mutableListOf<Int>()
                        while (hasNextElement()) {
                            val el0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                            col0.add(el0)
                        }
                        col0
                    }
                TEXT_DESCRIPTOR.index -> builder.text = deserializeString()
                null -> break@loop
                else -> skipValue()
            }
        }
    }
    return builder.build()
}
