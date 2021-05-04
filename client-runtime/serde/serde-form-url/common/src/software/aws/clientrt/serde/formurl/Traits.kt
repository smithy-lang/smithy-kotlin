/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.formurl

import software.aws.clientrt.serde.FieldTrait

/**
 * Specifies a name that a field is encoded into for form-url elements.
 */
data class FormUrlSerialName(val name: String) : FieldTrait

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
data class QueryLiteral(val key: String, val value: String) : FieldTrait
