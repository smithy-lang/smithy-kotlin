/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.formurl

import aws.smithy.kotlin.runtime.serde.FieldTrait
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor

/**
 * Specifies a name that a field is encoded into for form-url elements.
 */
public data class FormUrlSerialName(public val name: String) : FieldTrait

/**
 * Trait that adds a static `key=value` pair to a form-url encoded object
 *
 * ## Example
 *
 * This would add `Action=FooOperation&Version=2015-03-31` as key/value pairs to the serialized form of the object
 *
 * ```
 * val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
 *     trait(QueryLiteral("Action", "FooOperation")
 *     trait(QueryLiteral("Version", "2015-03-31")
 * }
 * ```
 */
public data class QueryLiteral(public val key: String, public val value: String) : FieldTrait

/**
 * Indicates that the container should be serialized in "flattened" form.
 * See: https://awslabs.github.io/smithy/1.0/spec/aws/aws-query-protocol.html#collections
 */
public object FormUrlFlattened : FieldTrait

/**
 * Specifies member name used when encoding a List/Set structure.
 * See https://awslabs.github.io/smithy/1.0/spec/aws/aws-query-protocol.html#collections
 *
 * This trait need only be added to a [SdkFieldDescriptor] if the element name is something
 * other than the default specified in [FormUrlCollectionName.Default]
 *
 * @param name the name to use which prefixes each list or set member
 */
public data class FormUrlCollectionName(public val member: String) : FieldTrait {
    public companion object {
        /**
         * The default serialized name for a list or set member.
         * This default is specified here: https://awslabs.github.io/smithy/1.0/spec/aws/aws-query-protocol.html#query-key-resolution
         */
        public val Default: FormUrlCollectionName = FormUrlCollectionName("member")
    }
}
/**
 * Specifies key and value node names used to encode a Map structure.
 * See https://awslabs.github.io/smithy/1.0/spec/aws/aws-query-protocol.html#maps
 *
 * This trait need only be added to a [SdkFieldDescriptor] if the map entry, key, or value is something
 * other than the default specified in [FormUrlMapName.Default]
 *
 * @param key the name of the key field
 * @param value the name of the value field
 */
public data class FormUrlMapName(
    public val key: String = Default.key,
    public val value: String = Default.value,
) : FieldTrait {
    public companion object {
        /**
         * The default serialized names for aspects of a Map in XML.
         * These defaults are specified here: https://awslabs.github.io/smithy/spec/xml.html#map-serialization
         */
        public val Default: FormUrlMapName = FormUrlMapName("key", "value")
    }
}
