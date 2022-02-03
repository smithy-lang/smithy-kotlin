/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.shapes.Shape

/**
 * Property bag keys used by symbol provider implementation
 */
object SymbolProperty {
    // The key that holds the default value for a type (symbol) as a string
    const val DEFAULT_VALUE_KEY: String = "defaultValue"

    // Boolean property indicating this symbol should be boxed
    const val BOXED_KEY: String = "boxed"

    // the original shape the symbol was created from
    const val SHAPE_KEY: String = "shape"

    // Mutable collection type
    const val MUTABLE_COLLECTION_FUNCTION: String = "mutableCollectionType"

    // Immutable collection type
    const val IMMUTABLE_COLLECTION_FUNCTION: String = "immutableCollectionType"

    // Entry type for Maps
    const val ENTRY_EXPRESSION: String = "entryExpression"

    // Pseudo dependency on a snippet of code
    const val GENERATED_DEPENDENCY: String = "generatedDependency"
}

/**
 * Test if a symbol is boxed
 */
val Symbol.isBoxed: Boolean
    get() = getProperty(SymbolProperty.BOXED_KEY).map {
        when (it) {
            is Boolean -> it
            else -> false
        }
    }.orElse(false)

/**
 * Test if a symbol is not boxed
 */
val Symbol.isNotBoxed: Boolean
    get() = !isBoxed

/**
 * Gets the default value for the symbol if present, else null
 * @param defaultBoxed the string to pass back for boxed values
 */
fun Symbol.defaultValue(defaultBoxed: String? = "null"): String? {
    // boxed types should always be defaulted to null
    if (isBoxed) {
        return defaultBoxed
    }

    val default = getProperty(SymbolProperty.DEFAULT_VALUE_KEY, String::class.java)
    return if (default.isPresent) default.get() else null
}

/**
 * Mark a symbol as being boxed (nullable) i.e. `T?`
 */
fun Symbol.Builder.boxed(): Symbol.Builder = apply { putProperty(SymbolProperty.BOXED_KEY, true) }

/**
 * Set the default value used when formatting the symbol
 */
fun Symbol.Builder.defaultValue(value: String): Symbol.Builder = apply { putProperty(SymbolProperty.DEFAULT_VALUE_KEY, value) }

/**
 * Convenience function for specifying kotlin namespace
 */
fun Symbol.Builder.namespace(name: String): Symbol.Builder = namespace(name, ".")

/**
 * Convenience function for specifying a symbol is a subnamespace of the [dependency].
 *
 * This will implicitly add the dependecy to the builder since it is known that it comes from the
 * dependency namespace.
 *
 * Equivalent to:
 * ```
 * builder.addDependency(dependency)
 * builder.namespace("${dependency.namespace}.subnamespace")
 * ```
 */
fun Symbol.Builder.namespace(dependency: KotlinDependency, subnamespace: String = ""): Symbol.Builder {
    addDependency(dependency)
    return if (subnamespace.isEmpty()) {
        namespace(dependency.namespace)
    } else {
        namespace("${dependency.namespace}.${subnamespace.trimStart('.')}")
    }
}

/**
 * Add a reference to a symbol coming from a kotlin dependency
 */
fun Symbol.Builder.addReference(dependency: KotlinDependency, name: String, subnamespace: String = ""): Symbol.Builder {
    val refSymbol = Symbol.builder()
        .name(name)
        .namespace(dependency, subnamespace)
        .build()
    return addReference(refSymbol)
}

/**
 * Add a reference to the given symbol with the context option
 */
fun Symbol.Builder.addReference(symbol: Symbol, option: SymbolReference.ContextOption): Symbol.Builder {
    val ref = SymbolReference.builder()
        .symbol(symbol)
        .options(option)
        .build()

    return addReference(ref)
}

/**
 * Get the shape this symbol was created from
 */
val Symbol.shape: Shape?
    get() = getProperty(SymbolProperty.SHAPE_KEY, Shape::class.java).getOrNull()
