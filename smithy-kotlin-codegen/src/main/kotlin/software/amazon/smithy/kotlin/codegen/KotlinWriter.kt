/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.traits.EnumDefinition
import software.amazon.smithy.utils.CodeWriter
import java.util.function.BiFunction

/**
 * Extension function that is more idiomatic Kotlin that is roughly the same purpose as
 * the provided function `openBlock(String textBeforeNewline, String textAfterNewline, Runnable r)`
 *
 * Example:
 * ```
 * writer.withBlock("{", "}") {
 *     write("foo")
 * }
 * ```
 *
 * Equivalent to:
 * ```
 * writer.openBlock("{")
 * writer.write("foo")
 * writer.closeBlock("}")
 * ```
 */
fun CodeWriter.withBlock(textBeforeNewLine: String, textAfterNewLine: String, block: CodeWriter.() -> Unit): CodeWriter {
    openBlock(textBeforeNewLine)
    block(this)
    closeBlock(textAfterNewLine)
    return this
}

fun CodeWriter.withState(state: String, block: CodeWriter.() -> Unit = {}): CodeWriter {
    pushState(state)
    block(this)
    popState()
    return this
}

// Convenience function to create symbol and add it as an import.
fun KotlinWriter.addImport(name: String, dependency: KotlinDependency = KotlinDependency.CLIENT_RT_CORE, namespace: String = dependency.namespace) {
    val importSymbol = Symbol.builder()
            .name(name)
            .namespace(namespace, ".")
            .addDependency(dependency)
            .build()

    addImport(importSymbol, "", SymbolReference.ContextOption.DECLARE)
}

// Add one or more blank lines to the writer.
fun CodeWriter.blankLine(count: Int = 1) {
    repeat(count) { write("") }
}

// Used for sections, deals with delimiter occurring within set but not trailing or leading.
fun CodeWriter.appendWithDelimiter(previousText: Any?, text: String, delimiter: String = ", ") {
    when {
        previousText !is String -> error("Unexpected type ${previousText?.javaClass?.canonicalName ?: "[UNKNOWN]"}")
        previousText.isEmpty() -> write(text)
        else -> write("$previousText$delimiter$text")
    }
}

class KotlinWriter(private val fullPackageName: String) : CodeWriter() {
    init {
        trimBlankLines()
        trimTrailingSpaces()
        setIndentText("    ")
        // type with default set
        putFormatter('D', KotlinSymbolFormatter(setDefault = true))
        // type only
        putFormatter('T', KotlinSymbolFormatter())
    }

    internal val dependencies: MutableList<SymbolDependency> = mutableListOf()
    private val imports = ImportDeclarations()

    fun addImport(symbol: Symbol, alias: String = "", vararg options: SymbolReference.Option) {
        // always add dependencies
        dependencies.addAll(symbol.dependencies)

        // only add imports for symbols in a different namespace
        if (!symbol.namespace.isEmpty() && symbol.namespace != fullPackageName) {
            imports.addImport(symbol.namespace, symbol.name, alias)
        }
    }

    fun addImportReferences(symbol: Symbol, vararg options: SymbolReference.ContextOption) {
        symbol.references.forEach { reference ->
            for (option in options) {
                if (reference.hasOption(option)) {
                    addImport(reference.symbol, reference.alias, *options)
                    break
                }
            }
        }
    }

    /**
     * Directly add an import
     */
    fun addImport(packageName: String, symbolName: String, alias: String = "") = imports.addImport(packageName, symbolName, alias)

    override fun toString(): String {
        val contents = super.toString()
        val header = "// Code generated by smithy-kotlin-codegen. DO NOT EDIT!\n\n"
        val importStatements = "${imports}\n\n"
        val pkgDecl = "package $fullPackageName\n\n"
        return header + pkgDecl + importStatements + contents
    }

    /**
     * Configures the writer with the appropriate opening/closing doc comment lines and calls the [block]
     * with this writer. Any calls to `write()` inside of block will be escaped appropriately.
     * On return the writer's original state is restored.
     *
     * e.g.
     * ```
     * writer.dokka(){
     *     write("This is a doc comment")
     * }
     * ```
     *
     * would output
     *
     * ```
     * /**
     *  * This is a doc comment
     *  */
     * ```
     */
    fun dokka(block: KotlinWriter.() -> Unit) {
        pushState()
        write("/**")
        setNewlinePrefix(" * ")
        block(this)
        popState()
        write(" */")
    }

    fun dokka(docs: String) {
        dokka {
            write(sanitizeDocumentation(docs))
        }
    }

    // handles the documentation for shapes
    fun renderDocumentation(shape: Shape) {
        shape.getTrait(DocumentationTrait::class.java).ifPresent {
            dokka(it.value)
        }
    }

    // handles the documentation for member shapes
    fun renderMemberDocumentation(model: Model, shape: MemberShape) {
        if (shape.getTrait(DocumentationTrait::class.java).isPresent) {
            dokka(shape.getTrait(DocumentationTrait::class.java).get().value)
        } else if (shape.getMemberTrait(model, DocumentationTrait::class.java).isPresent) {
            dokka(shape.getMemberTrait(model, DocumentationTrait::class.java).get().value)
        }
    }

    // handles the documentation for enum definitions
    fun renderEnumDefinitionDocumentation(enumDefinition: EnumDefinition) {
        enumDefinition.documentation.ifPresent {
            dokka(it)
        }
    }

    private fun sanitizeDocumentation(doc: String): String {
        // Docs can have valid $ characters that shouldn't run through formatters.
        return doc.replace("\$", "\$\$")
    }

    /**
     * Implements Kotlin symbol formatting for the `$T` formatter
     */
    private class KotlinSymbolFormatter(val setDefault: Boolean = false) : BiFunction<Any, String, String> {
        override fun apply(type: Any, indent: String): String {
            when (type) {
                is Symbol -> {
                    var formatted = type.name
                    if (type.isBoxed()) {
                        formatted += "?"
                    }

                    val defaultValue = type.defaultValue()
                    if (defaultValue != null && setDefault) {
                        formatted += " = $defaultValue"
                    }
                    return formatted
                }
//                is SymbolReference -> {
//                    return type.alias
//                }
                else -> throw CodegenException("Invalid type provided for \$T. Expected a Symbol, but found `$type`")
            }
        }
    }
}