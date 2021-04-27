/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency

class SymbolBuilderTest {

    @Test
    fun `it builds symbols`() {
        val x = buildSymbol {
            name = "Foo"
            dependencies += KotlinDependency.CLIENT_RT_CORE
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
}
