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

// FIXME - move this it's not a utility and only used in serde generation
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
internal fun Int.nestedDescriptorName(): String = "_c$this"

/**
 * Get the value if present otherwise return null
 */
fun <T> Optional<T>.getOrNull(): T? = if (isPresent) get() else null

/**
 * Split a string on word boundaries
 */
fun String.splitOnWordBoundaries(): List<String> {
    // adapted from Java v2 SDK CodegenNamingUtils.splitOnWordBoundaries
    var result = this

    // all non-alphanumeric characters: "acm-success"-> "acm success"
    result = result.replace(Regex("[^A-Za-z0-9+]"), " ")

    // if a number has a standalone v in front of it, separate it out
    result = result.replace(Regex("([^a-z]{2,})v([0-9]+)"), "$1 v$2 ") // TESTv4 -> "TEST v4 "
        .replace(Regex("([^A-Z]{2,})V([0-9]+)"), "$1 V$2 ") // TestV4 -> "Test V4 "

    // add a space between camelCased words
    result = result.split(Regex("(?<=[a-z])(?=[A-Z]([a-zA-Z]|[0-9]))")).joinToString(separator = " ") // AcmSuccess -> // "Acm Success"

    // add a space after acronyms
    result = result.replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2") // "ACMSuccess" -> "ACM Success"

    // add space after a number in the middle of a word
    result = result.replace(Regex("([0-9])([a-zA-Z])"), "$1 $2") // "s3ec2" -> "s3 ec2"

    // remove extra spaces - multiple consecutive ones or those and the beginning/end of words
    result = result.replace(Regex("\\s+"), " ") // "Foo  Bar" -> "Foo Bar"
        .trim() // " Foo " -> "Foo"

    return result.split(" ")
}

/**
 * Convert a string to `PascalCase` (uppercase start with upper case word boundaries)
 */
fun String.toPascalCase(): String = splitOnWordBoundaries().joinToString(separator = "") { it.toLowerCase().capitalize() }

/**
 * Convert a string to `camelCase` (lowercase start with upper case word boundaries)
 */
fun String.toCamelCase(): String = toPascalCase().decapitalize()
