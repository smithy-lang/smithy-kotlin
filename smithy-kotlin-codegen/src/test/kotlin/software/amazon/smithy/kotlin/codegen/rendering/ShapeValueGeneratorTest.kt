/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.NumberNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.node.ToNode
import software.amazon.smithy.model.shapes.*

class ShapeValueGeneratorTest {

    @Test
    fun `it renders maps`() {
        val model = """
            map MyMap {
                key: String,
                value: Integer,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val mapShape = model.expectShape(ShapeId.from("foo.bar#MyMap"))
        val writer = KotlinWriter("test")

        val params = Node.objectNodeBuilder()
            .withMember("k1", 1)
            .withMember("k2", 2)
            .withMember("k3", 3)
            .build()

        ShapeValueGenerator(model, provider).writeShapeValueInline(writer, mapShape, params)
        val contents = writer.toString()

        // FIXME - can't seem to get indentation quite right in our node visitor...
        val expected = """
mapOf<String, Int>(
    "k1" to 1,
    "k2" to 2,
    "k3" to 3
)
"""

        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders lists`() {
        val model = """
            list MyList {
                member: String,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        println(model.toSmithyIDL())

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val mapShape = model.expectShape(ShapeId.from("foo.bar#MyList"))
        val writer = KotlinWriter("test")

        val values: Array<Node> = listOf("v1", "v2", "v3").map(Node::from).toTypedArray()
        val params = Node.arrayNode(*values)

        ShapeValueGenerator(model, provider).writeShapeValueInline(writer, mapShape, params)
        val contents = writer.toString()

        val expected = """
listOf<String>(
    "v1",
    "v2",
    "v3"
)
        """.trimIndent()

        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders structs`() {
        val model = """
            structure MyStruct {
                stringMember: String,
                boolMember: Boolean,
                intMember: Integer,
                structMember: Nested,
                enumMember: MyEnum,
                floatMember: Float,
                doubleMember: Double,
                nullMember: String,
            }

            structure Nested {
                tsMember: Timestamp,
            }

            @enum([
                {
                    value: "fooey",
                    name: "FOO",
                },
            ])
            string MyEnum
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")

        val structShape = model.expectShape(ShapeId.from("foo.bar#MyStruct"))
        val writer = KotlinWriter("test")

        val params = Node.objectNodeBuilder()
            .withMember("stringMember", "v1")
            .withMember("boolMember", true)
            .withMember("intMember", 1)
            .withMember(
                "structMember",
                Node.objectNodeBuilder()
                    .withMember("tsMember", 11223344)
                    .build()
            )
            .withMember("enumMember", "fooey")
            .withMember("floatMember", 2)
            .withMember("doubleMember", 3.0)
            .withMember("nullMember", Node.nullNode())
            .build()

        ShapeValueGenerator(model, provider).writeShapeValueInline(writer, structShape, params)
        val contents = writer.toString()

        val expected = """
MyStruct {
    stringMember = "v1"
    boolMember = true
    intMember = 1
    structMember = Nested {
        tsMember = Instant.fromEpochSeconds(11223344, 0)
    }

    enumMember = MyEnum.fromValue("fooey")
    floatMember = 2.toFloat()
    doubleMember = 3.0.toDouble()
    nullMember = null
}
        """.trimIndent()

        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders unions`() {
        val model = """
            structure Nested {
                tsMember: Timestamp,
            }

            union MyUnion {
                stringMember: String,
                boolMember: Boolean,
                intMember: Integer,
                structMember: Nested,
                enumMember: MyEnum,
                floatMember: Float,
                doubleMember: Double,
                nullMember: String,
            }

            @enum([
                {
                    value: "fooey",
                    name: "FOO",
                },
            ])
            string MyEnum
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        println(model.toSmithyIDL())

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")

        val unionShape = model.expectShape(ShapeId.from("foo.bar#MyUnion"))
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)

        val params = Node.objectNodeBuilder()
            .withMember("stringMember", "v1")
            .build()

        ShapeValueGenerator(model, provider).writeShapeValueInline(writer, unionShape, params)
        val contents = writer.toString()

        val expected = """
MyUnion.StringMember("v1")
        """.trimIndent()

        contents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders numbers`() {
        for (type in listOf("Double", "Float")) {
            val model = """
                structure MyStruct {
                    member: $type,
                }
            """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

            val shape = model.expectShape(ShapeId.from("foo.bar#MyStruct"))
            val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
            val gen = ShapeValueGenerator(model, provider)

            val tests = mapOf<ToNode, String>(
                NumberNode.from(1.2) to "1.2.to$type()",
                StringNode.from("-Infinity") to "$type.NEGATIVE_INFINITY",
                StringNode.from("Infinity") to "$type.POSITIVE_INFINITY",
                StringNode.from("NaN") to "$type.NaN",
            )

            for ((nodeValue, serialization) in tests) {
                val writer = KotlinWriter(TestModelDefault.NAMESPACE)

                val node = Node.objectNodeBuilder()
                    .withMember("member", nodeValue)
                    .build()

                gen.writeShapeValueInline(writer, shape, node)
                val contents = writer.toString()

                val expected = """
                    MyStruct {
                        member = $serialization
                    }
                """.trimIndent()
                contents.shouldContainOnlyOnce(expected)
            }
        }
    }
}
