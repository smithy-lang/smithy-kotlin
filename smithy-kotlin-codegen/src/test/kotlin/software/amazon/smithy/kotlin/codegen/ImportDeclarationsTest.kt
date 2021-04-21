/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.test.TestDefault

class ImportDeclarationsTest {
    @Test
    fun `it renders imports`() {

        val decls = ImportDeclarations()

        decls.addImport(TestDefault.NAMESPACE, "Foo")
        decls.addImport("foo.bar", "Baz", alias = "Quux")

        val statements = decls.toString()
        val expected = "import ${TestDefault.NAMESPACE}.Foo\nimport foo.bar.Baz as Quux"
        assertEquals(expected, statements)
    }

    @Test
    fun `it filters duplicates`() {
        val decls = ImportDeclarations()

        decls.addImport(TestDefault.NAMESPACE, "Foo")
        decls.addImport(TestDefault.NAMESPACE, "Foo", "Foo")

        val statements = decls.toString()
        val expected = "import ${TestDefault.NAMESPACE}.Foo"
        assertEquals(expected, statements)
    }

    @Test
    fun `it renders without alias when symbol matches`() {
        val decls = ImportDeclarations()

        decls.addImport(TestDefault.NAMESPACE, "Foo", "Foo")

        val statements = decls.toString()
        val expected = "import ${TestDefault.NAMESPACE}.Foo"
        assertEquals(expected, statements)
    }
}
