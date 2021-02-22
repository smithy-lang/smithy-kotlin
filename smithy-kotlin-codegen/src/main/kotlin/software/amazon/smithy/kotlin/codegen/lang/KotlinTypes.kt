/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.buildSymbol

/**
 * Builtin kotlin types
 */
object KotlinTypes {
    val Int: Symbol = builtInSymbol("Int")
    val Long: Symbol = builtInSymbol("Long")
    val Float: Symbol = builtInSymbol("Float")
    val Double: Symbol = builtInSymbol("Double")
    val String: Symbol = builtInSymbol("String")
    val Unit: Symbol = builtInSymbol("Unit")
    val Boolean: Symbol = builtInSymbol("Boolean")
    val Any: Symbol = builtInSymbol("Any")
}

private fun builtInSymbol(symbol: String): Symbol = buildSymbol {
    name = symbol
    namespace = "kotlin"
    nullable = false
}

/**
 * Test if a string is a valid Kotlin identifier name
 */
fun isValidKotlinIdentifier(s: String): Boolean {
    val c = s.firstOrNull() ?: return false
    return when (c) {
        in 'a'..'z', in 'A'..'Z', '_' -> true
        else -> false
    }
}

/**
 * Flag indicating if this symbol is a Kotlin built-in symbol
 */
val Symbol.isBuiltIn: Boolean
    get() = namespace == "kotlin"
