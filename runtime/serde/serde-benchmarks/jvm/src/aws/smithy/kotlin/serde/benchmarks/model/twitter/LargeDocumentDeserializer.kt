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
import aws.smithy.kotlin.runtime.serde.deserializeStruct
import aws.smithy.kotlin.runtime.serde.json.JsonSerialName


internal suspend fun deserializeLargeDocument(deserializer: Deserializer): Large {
    val builder = Large.builder()
    val H_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("h"))
    val RESIZE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("resize"))
    val W_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("w"))
    val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        field(H_DESCRIPTOR)
        field(RESIZE_DESCRIPTOR)
        field(W_DESCRIPTOR)
    }

    deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
        loop@while (true) {
            when (findNextFieldIndex()) {
                H_DESCRIPTOR.index -> builder.h = deserializeInt()
                RESIZE_DESCRIPTOR.index -> builder.resize = deserializeString()
                W_DESCRIPTOR.index -> builder.w = deserializeInt()
                null -> break@loop
                else -> skipValue()
            }
        }
    }
    return builder.build()
}
