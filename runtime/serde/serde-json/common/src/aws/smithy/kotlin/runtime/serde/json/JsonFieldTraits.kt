/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.serde.FieldTrait
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.expectTrait

/**
 * Specifies a name that a field is encoded into for Json elements.
 */
@InternalApi
public data class JsonSerialName(public val name: String) : FieldTrait {
    override val serialName: String
        get() = name

    internal val escaped: String by lazy { "\"${name.escape()}\"" }
}

/**
 * Provides the serialized name of the field.
 */
@InternalApi
public val SdkFieldDescriptor.serialName: String
    get() = expectTrait<JsonSerialName>().name

/**
 * Provides the pre-escaped, quoted JSON name of the field (see [JsonSerialName.escaped]).
 */
internal val SdkFieldDescriptor.escapedSerialName: String
    get() = expectTrait<JsonSerialName>().escaped

/**
 * Indicates to deserializers to ignore field/key
 */
@InternalApi
public data class IgnoreKey(public val key: String) : FieldTrait
