/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.core

import aws.smithy.kotlin.codegen.test.TestModelDefault
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportDeclarationsTest {
    @Test
    fun `it renders imports`() {
        val decls = ImportDeclarations()

        decls.addImport(TestModelDefault.NAMESPACE, "Foo")
        decls.addImport("foo.bar", "Baz", alias = "Quux")

        val statements = decls.toString()
        val expected = "import ${TestModelDefault.NAMESPACE}.Foo\nimport foo.bar.Baz as Quux"
        assertEquals(expected, statements)
    }

    @Test
    fun `it filters duplicates`() {
        val decls = ImportDeclarations()

        decls.addImport(TestModelDefault.NAMESPACE, "Foo")
        decls.addImport(TestModelDefault.NAMESPACE, "Foo", "Foo")

        val statements = decls.toString()
        val expected = "import ${TestModelDefault.NAMESPACE}.Foo"
        assertEquals(expected, statements)
    }

    @Test
    fun `it renders without alias when symbol matches`() {
        val decls = ImportDeclarations()

        decls.addImport(TestModelDefault.NAMESPACE, "Foo", "Foo")

        val statements = decls.toString()
        val expected = "import ${TestModelDefault.NAMESPACE}.Foo"
        assertEquals(expected, statements)
    }
}
