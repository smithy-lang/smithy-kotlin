/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

/**
 * Container and formatter for Kotlin imports
 */
class ImportDeclarations {

    fun addImport(packageName: String, symbolName: String, alias: String = "") {
        imports.add(ImportStatement(packageName, symbolName, alias))
    }

    override fun toString(): String {
        if (imports.isEmpty()) {
            return ""
        }

        return imports
            .map(ImportStatement::statement)
            .sorted()
            .joinToString(separator = "\n")
    }

    private val imports: MutableSet<ImportStatement> = mutableSetOf()
}

private data class ImportStatement(val packageName: String, val symbolName: String, val alias: String) {
    val statement: String
    get() {
        return if (alias != "" && alias != symbolName) {
            "import $packageName.$symbolName as $alias"
        } else {
            "import $packageName.$symbolName"
        }
    }

    override fun toString(): String = statement
}
