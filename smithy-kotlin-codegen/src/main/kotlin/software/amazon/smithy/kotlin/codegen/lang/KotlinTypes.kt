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
    )
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

/**
 * Escape characters in strings to ensure they are treated as pure literals.
 */
fun String.toEscapedLiteral(): String = replace("\$", "\\$")

/**
 * Return true if string is valid package namespace, false otherwise.
 */
fun String.isValidPackageName() = isNotEmpty() && !this.any { !it.isLetterOrDigit() && it != '.' }
