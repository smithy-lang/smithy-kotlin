/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.core

/**
 * Container and formatter for Kotlin imports
 */
class ImportDeclarations {

    fun addImport(packageName: String, symbolName: String, alias: String = "", importRemoved: (ImportStatement) -> Unit = {}) {
        val canonicalAlias = if (alias == symbolName) "" else alias

        // Collect any existing types that conflict with the new import
        val collidedTypes = imports.filter {
            it.alias == "" && it.symbolName == symbolName && it.packageName != packageName && symbolName != "*"
        }
        val typeNameCollision = collidedTypes.isNotEmpty()

        val import = ImportStatement(packageName, symbolName, canonicalAlias)

        // If multiple imports specify the same name but different packages, we
        // favor keeping known SDK types in imports and fully qualifying
        // symbols coming from models.
        if (typeNameCollision) {
            //mark for full qualification
            importRemoved(import)
            return
        }

        imports.add(import)
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

data class ImportStatement(val packageName: String, val symbolName: String, val alias: String) {
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

private fun String.isSdkRuntimePackage() : Boolean =
    startsWith("software.aws.clientrt.") || startsWith("aws.sdk.kotlin.runtime.")