/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import kotlin.test.*
import kotlin.test.Test

class SymbolBuilderTest {

    @Test
    fun `it builds symbols`() {
        val x = buildSymbol {
            name = "Foo"
            dependencies += KotlinDependency.CORE
            reference {
                name = "MyRef"
            }
            namespace = "com.mypkg"
            definitionFile = "Foo.kt"
            declarationFile = "Foo.kt"
            defaultValue = "fooey"
            properties {
                set("key", "value")
                set("key2", "value2")
                remove("key2")
            }
        }

        assertEquals("Foo", x.name)
        assertEquals("com.mypkg", x.namespace)
        assertEquals("Foo.kt", x.declarationFile)
        assertEquals("Foo.kt", x.definitionFile)
        assertEquals("value", x.getProperty("key").get())
        assertFalse(x.getProperty("key2").isPresent)
        assertEquals(1, x.references.size)
        assertEquals(1, x.dependencies.size)
    }

    @Test
    fun `it builds symbol from String`() {
        val expected = buildSymbol {
            name = "Foo"
            namespace = "x.y.z"
        }

        assertEquals(expected, "x.y.z.Foo".toSymbol())
    }

    @Test
    fun `it builds symbol without namespace String`() {
        val expected = buildSymbol {
            name = "Foo"
        }

        assertEquals(expected.namespace, "Foo".toSymbol().namespace)
        assertEquals(expected.name, "Foo".toSymbol().name)
    }
}
