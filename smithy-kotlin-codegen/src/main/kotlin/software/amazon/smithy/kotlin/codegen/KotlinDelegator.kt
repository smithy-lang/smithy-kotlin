/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import java.nio.file.Paths
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape

private const val DEFAULT_SOURCE_SET_ROOT = "./src/main/kotlin/"
private const val DEFAULT_TEST_SOURCE_SET_ROOT = "./src/test/kotlin/"

/**
 * Manages writers for Kotlin files.
 */
class KotlinDelegator(
    private val settings: KotlinSettings,
    private val model: Model,
    private val fileManifest: FileManifest,
    private val symbolProvider: SymbolProvider,
    private val integrations: List<KotlinIntegration> = listOf()
) {

    private val writers: MutableMap<String, KotlinWriter> = mutableMapOf()

    /**
     * Writes all pending writers to disk and then clears them out.
     */
    fun flushWriters() {
        writers.forEach { (filename, writer) ->
            fileManifest.writeFile(filename, writer.toString())
        }
        writers.clear()
    }

    /**
     * Gets all of the dependencies that have been registered in writers owned by the delegator.
     *
     * @return Returns all the dependencies.
     */
    val dependencies: List<SymbolDependency>
        get() {
            return writers.values.flatMap(KotlinWriter::dependencies)
        }

    /**
     * Gets a previously created writer or creates a new one if needed.
     *
     * @param shape Shape to create the writer for.
     * @param block Consumer that accepts and works with the file.
     */
    fun useShapeWriter(
        shape: Shape,
        block: (KotlinWriter) -> Unit
    ) {
        val symbol = symbolProvider.toSymbol(shape)
        useShapeWriter(symbol, block)
    }

    /**
     * Gets a previously created writer or creates a new one if needed.
     *
     * @param symbol Symbol to create the writer for.
     * @param block Lambda that accepts and works with the file.
     */
    fun useShapeWriter(
        symbol: Symbol,
        block: (KotlinWriter) -> Unit
    ) {
        val writer: KotlinWriter = checkoutWriter(symbol.definitionFile, symbol.namespace)

        // Add any needed DECLARE symbols.
        writer.addImportReferences(symbol, SymbolReference.ContextOption.DECLARE)
        writer.dependencies.addAll(symbol.dependencies)
        writer.pushState()

        // shape is stored in the property bag when generated, if it's there pull it back out
        val shape = symbol.getProperty("shape", Shape::class.java)
        if (shape.isPresent) {
            // Allow integrations to do things like add onSection callbacks.
            // these onSection callbacks are removed when popState is called.
            for (integration in integrations) {
                integration.onShapeWriterUse(settings, model, symbolProvider, writer, shape.get())
            }
        }

        block(writer)
        writer.popState()
    }

    /**
     * Gets a previously created writer or creates a new one if needed
     * and adds a new line if the writer already exists.
     *
     * @param filename Name of the file to create.
     * @param block Lambda that accepts and works with the file.
     */
    fun useFileWriter(filename: String, namespace: String, block: (KotlinWriter) -> Unit) {
        val writer: KotlinWriter = checkoutWriter(filename, namespace)
        block(writer)
    }

    /**
     * Gets a previously created test file writer or creates a new one if needed
     * and adds a new line if the writer already exists.
     *
     * @param filename Name of the file to create.
     * @param block Lambda that accepts and works with the file.
     */
    fun useTestFileWriter(filename: String, namespace: String, block: (KotlinWriter) -> Unit) {
        val writer: KotlinWriter = checkoutWriter(filename, namespace, DEFAULT_TEST_SOURCE_SET_ROOT)
        block(writer)
    }

    private fun checkoutWriter(
        filename: String,
        namespace: String,
        sourceSetRoot: String = DEFAULT_SOURCE_SET_ROOT
    ): KotlinWriter {
        // src/main/kotlin/namespace/filename
        val root = sourceSetRoot + namespace.replace(".", "/")
        val formattedFilename = Paths.get(root, filename).normalize().toString()
        val needsNewline = writers.containsKey(formattedFilename)
        val writer = writers.getOrPut(formattedFilename) { KotlinWriter(namespace) }

        if (needsNewline) {
            writer.write("\n")
        }
        return writer
    }
}
