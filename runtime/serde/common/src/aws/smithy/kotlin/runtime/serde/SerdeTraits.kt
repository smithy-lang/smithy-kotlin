/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.InternalApi

/**
 * This tag interface provides a mechanism to attach type-specific metadata to any field.
 * See [aws.smithy.kotlin.runtime.serde.xml.XmlList] for an example implementation.
 *
 * For example, to specify that a list should be serialized in XML such that values are wrapped
 * in a tag called "boo", pass an instance of XmlList to the FieldDescriptor of `XmlList(elementName="boo")`.
 */
@InternalApi
public interface FieldTrait

/**
 * Indicates to deserializers to ignore field/key
 *
 * @param key The key to ignore in the payload
 * @param regardlessOfInModel If true will ignore key even though the model indicates key should be there
 *
 */
@InternalApi
public data class IgnoreKey(public val key: String, public val regardlessOfInModel: Boolean = true) : FieldTrait

/**
 * Denotes that a Map or List may contain null values
 * Details at https://awslabs.github.io/smithy/1.0/spec/core/type-refinement-traits.html#sparse-trait
 */
@InternalApi
public object SparseValues : FieldTrait
