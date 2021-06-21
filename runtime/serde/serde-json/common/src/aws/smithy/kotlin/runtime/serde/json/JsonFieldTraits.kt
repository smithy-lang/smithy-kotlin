/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.FieldTrait
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.expectTrait

/**
 * Specifies a name that a field is encoded into for Json elements.
 */
data class JsonSerialName(val name: String) : FieldTrait

/**
 * Provides the serialized name of the field.
 */
val SdkFieldDescriptor.serialName: String
    get() = expectTrait<JsonSerialName>().name
