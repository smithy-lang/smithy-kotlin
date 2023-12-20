/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.NumberNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.node.ToNode
import software.amazon.smithy.model.shapes.*
import kotlin.test.Test

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

        ShapeValueGenerator(model, provider).instantiateShapeInline(writer, mapShape, params)
        val contents = writer.toString()

        val expected = """
mapOf<String, Int>(
    "k1" to 1,
    "k2" to 2,
    "k3" to 3
)
"""

        contents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders lists`() {
        val model = """
            list MyList {
                member: String,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val listShape = model.expectShape(ShapeId.from("foo.bar#MyList"))
        val writer = KotlinWriter("test")

        val values: Array<Node> = listOf("v1", "v2", "v3").map(Node::from).toTypedArray()
        val params = Node.arrayNode(*values)

        ShapeValueGenerator(model, provider).instantiateShapeInline(writer, listShape, params)
        val contents = writer.toString()

        val expected = """
listOf<String>(
    "v1",
    "v2",
    "v3"
)
        """.trimIndent()

        contents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders nested lists`() {
        val model = """
            list MyList {
                member: NestedList,
            }
            list NestedList {
                member: String
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val listShape = model.expectShape(ShapeId.from("foo.bar#MyList"))
        val writer = KotlinWriter("test")

        val values = listOf(
            listOf("v1", "v2", "v3"),
            listOf("v4", "v5", "v6"),
        ).map {
            Node.arrayNode(*it.map(Node::from).toTypedArray())
        }.toTypedArray()

        val params = Node.arrayNode(*values)

        ShapeValueGenerator(model, provider).instantiateShapeInline(writer, listShape, params)
        val contents = writer.toString()

        val expected = """
listOf<List<String>>(
    listOf<String>(
        "v1",
        "v2",
        "v3"
    ),
    listOf<String>(
        "v4",
        "v5",
        "v6"
    )
)
        """.trimIndent()

        contents.shouldContainOnlyOnceWithDiff(expected)
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
                    .build(),
            )
            .withMember("enumMember", "fooey")
            .withMember("floatMember", 2)
            .withMember("doubleMember", 3.0)
            .withMember("nullMember", Node.nullNode())
            .build()

        ShapeValueGenerator(model, provider).instantiateShapeInline(writer, structShape, params)
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

        contents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders null structs`() {
        val model = """
            structure MyStruct {
                stringMember: String,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")

        val structShape = model.expectShape(ShapeId.from("foo.bar#MyStruct"))
        val writer = KotlinWriter("test")

        val params = Node.nullNode()

        ShapeValueGenerator(model, provider).instantiateShapeInline(writer, structShape, params)
        val contents = writer.toString()

        val expected = """
            null
        """.trimIndent()

        contents.shouldContainOnlyOnceWithDiff(expected)
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

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")

        val unionShape = model.expectShape(ShapeId.from("foo.bar#MyUnion"))
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)

        val params = Node.objectNodeBuilder()
            .withMember("stringMember", "v1")
            .build()

        ShapeValueGenerator(model, provider).instantiateShapeInline(writer, unionShape, params)
        val contents = writer.toString()

        val expected = """
MyUnion.StringMember("v1")
        """.trimIndent()

        contents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders numbers`() {
        for (type in listOf("Double", "Float")) {
            testType(
                type,
                NumberNode.from(1.2) to "1.2.to$type()",
                StringNode.from("-Infinity") to "$type.NEGATIVE_INFINITY",
                StringNode.from("Infinity") to "$type.POSITIVE_INFINITY",
                StringNode.from("NaN") to "$type.NaN",
            )
        }
    }

    @Test
    fun `it renders big integers`() {
        testType(
            "BigInteger",
            NumberNode.from(100) to """BigInteger("100")""",
            StringNode.from("340282366920938463463374607431768211456") to """BigInteger("340282366920938463463374607431768211456")""",
        )
    }

    @Test
    fun `it renders big decimals`() {
        testType(
            "BigDecimal",
            NumberNode.from(100.23) to """BigDecimal("100.23")""",
            StringNode.from("0.340282366920938463463374607431768211456") to """BigDecimal("0.340282366920938463463374607431768211456")""",
        )
    }

    private fun testType(type: String, vararg testCases: Pair<ToNode, String>) {
        val model = """
            structure MyStruct {
                member: $type,
            }
        """.prependNamespaceAndService(namespace = "foo.bar").toSmithyModel()

        val shape = model.expectShape(ShapeId.from("foo.bar#MyStruct"))
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "foo.bar")
        val gen = ShapeValueGenerator(model, provider)

        for ((nodeValue, serialization) in testCases) {
            val writer = KotlinWriter(TestModelDefault.NAMESPACE)

            val node = Node.objectNodeBuilder()
                .withMember("member", nodeValue)
                .build()

            gen.instantiateShapeInline(writer, shape, node)
            val contents = writer.toString()

            val expected = """
                MyStruct {
                    member = $serialization
                }
            """.trimIndent()
            contents.shouldContainOnlyOnceWithDiff(expected)
        }
    }
}
