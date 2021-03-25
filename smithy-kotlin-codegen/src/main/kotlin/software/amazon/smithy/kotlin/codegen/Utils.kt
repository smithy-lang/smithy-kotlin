/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen

import java.util.Optional

/**
 * Specifies the type of value the identifier represents
 */
internal enum class NestedIdentifierType(val prefix: String) {
    KEY("k"), // Generated variable names for map keys
    VALUE("v"), // Generated variable names for map values
    ELEMENT("el"), // Generated variable name for list elements
    COLLECTION("col"), // Generated variable name for collection types (list, set)
    MAP("map"); // Generated variable name for map type
}

/**
 * Generate an identifier for a given nesting level
 * @param type intended type of value
 */
internal fun Int.variableNameFor(type: NestedIdentifierType): String = "${type.prefix}$this"

/**
 * Generate an identifier for a given nesting level
 */
internal fun Int.nestedDescriptorName(): String = "_C$this"

/**
 * Get the value if present otherwise return null
 */
fun <T> Optional<T>.getOrNull(): T? = if (isPresent) get() else null
