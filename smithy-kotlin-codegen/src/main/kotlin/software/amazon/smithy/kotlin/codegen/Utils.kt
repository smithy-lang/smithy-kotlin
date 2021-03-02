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

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.utils.CodeWriter
import java.util.*

/**
 * If is member shape returns target, otherwise returns self.
 * @param model for loading the target shape
 */
internal fun Shape.targetOrSelf(model: Model) = when (this) {
    is MemberShape -> model.expectShape(this.target)
    else -> this
}

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
 * Optionally call the [Runnable] if [test] is true, otherwise do nothing and return the instance without
 * running the block
 */
fun CodeWriter.callIf(test: Boolean, runnable: Runnable): CodeWriter {
    if (test) {
        runnable.run()
    }
    return this
}

/**
 * Kotlin sugar for hasTrait() check. e.g. shape.hasTrait<EnumTrait>() instead of shape.hasTrait(EnumTrait::class.java)
 */
inline fun <reified T : Trait> Shape.hasTrait(): Boolean = hasTrait(T::class.java)

/**
 * Kotlin sugar for expectTrait() check. e.g. shape.expectTrait<EnumTrait>() instead of shape.expectTrait(EnumTrait::class.java)
 */
inline fun <reified T : Trait> Shape.expectTrait(): T = expectTrait(T::class.java)

/**
 * Kotlin sugar for getTrait() check. e.g. shape.getTrait<EnumTrait>() instead of shape.getTrait(EnumTrait::class.java)
 */
inline fun <reified T : Trait> Shape.getTrait(): T? = getTrait(T::class.java).getOrNull()
