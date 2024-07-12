/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.serde.FieldTrait
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.expectTrait

/**
 * Specifies a CBOR name that a field is encoded into.
 */
@InternalApi
public data class CborSerialName(public val name: String) : FieldTrait

/**
 * Provides the serialized name of the field.
 */
@InternalApi
public val SdkFieldDescriptor.serialName: String
    get() = expectTrait<CborSerialName>().name
