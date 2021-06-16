/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.model.buildSymbol

/**
 * Builtin kotlin types
 */
object KotlinTypes {
    val Unit: Symbol = builtInSymbol("Unit")
    val Any: Symbol = builtInSymbol("Any")
    val Nothing: Symbol = builtInSymbol("Nothing")

    val String: Symbol = builtInSymbol("String")
    val Byte: Symbol = builtInSymbol("Byte")
    val UByte: Symbol = builtInSymbol("UByte")
    val Char: Symbol = builtInSymbol("Char")
    val ByteArray: Symbol = builtInSymbol("ByteArray")
    val UByteArray: Symbol = builtInSymbol("UByteArray")
    val CharArray: Symbol = builtInSymbol("CharArray")

    val Int: Symbol = builtInSymbol("Int")
    val Short: Symbol = builtInSymbol("Short")
    val Long: Symbol = builtInSymbol("Long")
    val UInt: Symbol = builtInSymbol("UInt")
    val UShort: Symbol = builtInSymbol("UShort")
    val ULong: Symbol = builtInSymbol("ULong")
    val Float: Symbol = builtInSymbol("Float")
    val Double: Symbol = builtInSymbol("Double")
    val Boolean: Symbol = builtInSymbol("Boolean")

    val List: Symbol = builtInSymbol("List", "kotlin.collections")
    val Set: Symbol = builtInSymbol("Set", "kotlin.collections")
    val Map: Symbol = builtInSymbol("Map", "kotlin.collections")

    /**
     * A (non-exhaustive) set of builtin Kotlin symbols
     */
    val All: Set<Symbol> = setOf(
        Unit,
        Any,
        Nothing,

        String,
        Byte,
        UByte,
        Char,
        ByteArray,
        UByteArray,
        CharArray,

        Int,
        Short,
        Long,
        UInt,
        UShort,
        ULong,
        Float,
        Double,
        Boolean,

        List,
        Set,
        Map
    )
}

private fun builtInSymbol(symbol: String, ns: String = "kotlin"): Symbol = buildSymbol {
    name = symbol
    namespace = ns
    nullable = false
}

/**
 * Test if a string is a valid Kotlin identifier
 *
 * https://kotlinlang.org/spec/syntax-and-grammar.html#grammar-rule-Identifier
 */
fun isValidKotlinIdentifier(s: String): Boolean {
    s.forEachIndexed { idx, chr ->
        val isLetterOrUnderscore = chr.isLetter() || chr == '_'
        when (idx) {
            0 -> if (!isLetterOrUnderscore) return false
            else -> if (!isLetterOrUnderscore && !chr.isDigit()) return false
        }
    }
    return true
}

/**
 * Flag indicating if this symbol is a Kotlin built-in symbol
 */
val Symbol.isBuiltIn: Boolean
    get() = namespace.startsWith("kotlin")

/**
 * Escape characters in strings to ensure they are treated as pure literals.
 */
fun String.toEscapedLiteral(): String = replace("\$", "\\$")

/**
 * Return true if string is valid package namespace, false otherwise.
 */
fun String.isValidPackageName() = isNotEmpty() && all { it.isLetterOrDigit() || it == '.' }
