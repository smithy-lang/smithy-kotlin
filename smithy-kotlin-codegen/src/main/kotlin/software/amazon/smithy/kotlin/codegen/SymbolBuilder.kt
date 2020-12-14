/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolDependencyContainer
import software.amazon.smithy.codegen.core.SymbolReference

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
    var namespace: String? = null

    var definitionFile: String? = null
    var declarationFile: String? = null
    var defaultValue: String? = null

    val dependencies: MutableList<SymbolDependencyContainer> = mutableListOf()
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

    fun setProperty(key: String, value: String) { builder.putProperty(key, value) }
    fun removeProperty(key: String) { builder.removeProperty(key) }
    fun properties(block: PropertiesBuilder.() -> Unit) {
        val propBuilder = object : PropertiesBuilder {
            override fun set(key: String, value: String) = setProperty(key, value)
            override fun remove(key: String) = removeProperty(key)
        }

        block(propBuilder)
    }

    interface PropertiesBuilder {
        fun set(key: String, value: String)
        fun remove(key: String)
    }

    fun build(): Symbol {
        builder.name(name)
        if (nullable) {
            builder.boxed()
        }

        namespace?.let { builder.namespace(namespace, ".") }
        declarationFile?.let { builder.declarationFile(it) }
        definitionFile?.let { builder.definitionFile(it) }
        defaultValue?.let { builder.defaultValue(it) }
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

fun SymbolBuilder.namespace(dependency: KotlinDependency, subnamespace: String = "") {
    namespace = if (subnamespace.isNotEmpty()) {
        "${dependency.namespace}.${subnamespace.trimStart('.')}"
    } else {
        dependency.namespace
    }

    dependency(dependency)
}
