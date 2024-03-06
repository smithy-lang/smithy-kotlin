/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.lang

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypePackage
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.toSymbol

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

    object Collections : RuntimeTypePackage(KotlinDependency.KOTLIN_STDLIB, "collections") {
        val List: Symbol = stdlibSymbol("List")
        val listOf: Symbol = stdlibSymbol("listOf")
        val MutableList: Symbol = stdlibSymbol("MutableList")
        val Map: Symbol = stdlibSymbol("Map")
        val mutableListOf: Symbol = stdlibSymbol("mutableListOf")
        val mutableMapOf: Symbol = stdlibSymbol("mutableMapOf")
        val Set: Symbol = stdlibSymbol("Set")

        private fun listType(
            listType: Symbol,
            target: Symbol,
            isNullable: Boolean = false,
            default: String? = null,
        ): Symbol = buildSymbol {
            name = "${listType.fullName}<${target.fullName}>"
            nullable = isNullable
            defaultValue = default
            reference(listType)
            reference(target)
            dependency(KotlinDependency.KOTLIN_STDLIB)
        }

        private fun setType(
            setType: Symbol,
            target: Symbol,
            isNullable: Boolean = false,
            default: String? = null,
        ): Symbol = buildSymbol {
            name = "${setType.fullName}<${target.fullName}>"
            nullable = isNullable
            defaultValue = default
            reference(setType)
            reference(target)
            dependency(KotlinDependency.KOTLIN_STDLIB)
        }

        /**
         * Convenience function to get a `List<target>` as a symbol
         */
        fun list(
            target: Symbol,
            isNullable: Boolean = false,
            default: String? = null,
        ): Symbol = listType(List, target, isNullable, default)

        /**
         * Convenience function to get a `MutableList<target>` as a symbol
         */
        fun mutableList(
            target: Symbol,
            isNullable: Boolean = false,
            default: String? = null,
        ): Symbol = listType(MutableList, target, isNullable, default)

        /**
         * Convenience function to get a `Set<target>` as a symbol
         */
        fun set(
            target: Symbol,
            isNullable: Boolean = false,
            default: String? = null,
        ): Symbol = setType(Set, target, isNullable, default)
    }

    object Jvm : RuntimeTypePackage(KotlinDependency.KOTLIN_STDLIB, "jvm") {
        val JvmName = stdlibSymbol("JvmName")
        val JvmStatic = stdlibSymbol("JvmStatic")
    }

    object Text : RuntimeTypePackage(KotlinDependency.KOTLIN_STDLIB, "text") {
        val encodeToByteArray = stdlibSymbol("encodeToByteArray")
    }

    object Time : RuntimeTypePackage(KotlinDependency.KOTLIN_STDLIB, "time") {
        val Duration = stdlibSymbol("Duration")
        val milliseconds = stdlibSymbol("milliseconds", "time.Duration.Companion")
        val minutes = stdlibSymbol("minutes", "time.Duration.Companion")
    }

    object Coroutines {
        val CoroutineContext = "kotlin.coroutines.CoroutineContext".toSymbol()
    }

    /**
     * A (non-exhaustive) set of builtin Kotlin symbols (and common stdlib symbols)
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

        Collections.List,
        Collections.Set,
        Collections.Map,
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
 * Flag indicating if this symbol is a Kotlin built-in symbol, and as such, doesn't need to be imported
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
fun String.isValidPackageName() = isNotEmpty() && all { it.isLetterOrDigit() || it == '.' }

private fun RuntimeTypePackage.stdlibSymbol(
    name: String,
    subpackage: String = defaultSubpackage,
): Symbol = symbol(name, subpackage, isExtension = false, nullable = false)
