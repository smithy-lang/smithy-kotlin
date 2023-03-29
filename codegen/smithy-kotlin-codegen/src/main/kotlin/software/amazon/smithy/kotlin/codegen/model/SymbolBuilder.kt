/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolDependencyContainer
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.core.GeneratedDependency
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.SymbolRenderer

@DslMarker
annotation class SymbolDsl

/**
 * Kotlin DSL wrapper around Symbol.Builder
 */
@SymbolDsl
open class SymbolBuilder {
    private val builder = Symbol.builder()
    var name: String? = null
    var nullable: Boolean = true
    var isExtension: Boolean = false
    var objectRef: Symbol? = null
    var namespace: String? = null

    var definitionFile: String? = null
    var declarationFile: String? = null
    var defaultValue: String? = null

    /**
     * Register the function (dependency) responsible for rendering this symbol.
     */
    var renderBy: SymbolRenderer? = null

    val dependencies: MutableSet<SymbolDependencyContainer> = mutableSetOf()
    val references: MutableList<SymbolReference> = mutableListOf()

    fun dependency(dependency: SymbolDependencyContainer) = dependencies.add(dependency)

    fun reference(ref: SymbolReference) = references.add(ref)

    fun reference(symbol: Symbol, vararg options: SymbolReference.ContextOption) {
        if (options.isEmpty()) {
            builder.addReference(symbol)
        } else {
            val ref = SymbolReference.builder()
                .symbol(symbol)
                .options(options.toSet())
                .build()
            references += ref
        }
    }

    fun reference(block: SymbolBuilder.() -> Unit) {
        val refSymbol = SymbolBuilder().apply(block).build()
        reference(refSymbol)
    }

    fun setProperty(key: String, value: Any) { builder.putProperty(key, value) }
    fun removeProperty(key: String) { builder.removeProperty(key) }
    fun properties(block: PropertiesBuilder.() -> Unit) {
        val propBuilder = object : PropertiesBuilder {
            override fun set(key: String, value: Any) = setProperty(key, value)
            override fun remove(key: String) = removeProperty(key)
        }

        block(propBuilder)
    }

    interface PropertiesBuilder {
        fun set(key: String, value: Any)
        fun remove(key: String)
    }

    fun build(): Symbol {
        builder.name(name)
        if (nullable) {
            builder.boxed()
        }
        builder.putProperty(SymbolProperty.IS_EXTENSION, isExtension)
        if (objectRef != null) {
            builder.putProperty(SymbolProperty.OBJECT_REF, objectRef)
        }

        namespace?.let { builder.namespace(namespace, ".") }
        declarationFile?.let { builder.declarationFile(it) }
        definitionFile?.let { builder.definitionFile(it) }
        defaultValue?.let { builder.defaultValue(it) }

        if (renderBy != null) {
            checkNotNull(name) { "a rendered dependency must declare a name!" }
            checkNotNull(definitionFile) { "a rendered dependency must declare a definition file!" }
            checkNotNull(namespace) { "a rendered dependency must declare a namespace" }
            // abuse dependencies to get the delegator to eventually render this
            val generatedDep = GeneratedDependency(name!!, namespace!!, definitionFile!!, renderBy!!)
            dependency(generatedDep)
        }

        dependencies.forEach { builder.addDependency(it) }
        references.forEach { builder.addReference(it) }

        return builder.build()
    }
}

/**
 * Build a symbol inside the given block
 */
fun buildSymbol(block: SymbolBuilder.() -> Unit): Symbol =
    SymbolBuilder().apply(block).build()

fun SymbolBuilder.namespace(dependency: KotlinDependency, subpackage: String = "") {
    namespace = if (subpackage.isNotEmpty()) {
        "${dependency.namespace}.${subpackage.trimStart('.')}"
    } else {
        dependency.namespace
    }

    dependency(dependency)
}

/**
 * Convert a String in "<package>.<name>" format to a symbol where the last segment of a '.' delimited list becomes
 * the name and the rest becomes the namespace.
 *
 * ex: com.foo.bar.Thing -> (name=Thing, namespace=com.foo.bar)
 */
fun String.toSymbol(): Symbol =
    buildSymbol {
        require(this@toSymbol.isNotBlank()) { "Invalid string to convert to symbol" }
        val segments = split(".")
        name = segments.last()
        namespace = segments
            .dropLast(1)
            .joinToString(separator = ".") { it }
    }
