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
