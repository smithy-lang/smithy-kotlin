/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.integration.SerializeStructGenerator
import software.amazon.smithy.kotlin.codegen.integration.SerializeUnionGenerator
import software.amazon.smithy.kotlin.codegen.integration.SerializeUnionGenerator2
import software.amazon.smithy.kotlin.codegen.integration.stripCodegenPrefix
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait

class SerializeUnionGeneratorTest {
    private val modelPrefix = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    Foo,
                ]
            }

            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                input: FooRequest
            }        
    """.trimIndent()

    private val defaultModel = javaClass.getResource("http-binding-protocol-generator-test.smithy").asSmithy()

    /**
     * Get the contents for the given shape ID which should either be
     * an operation shape or a structure shape. In the case of an operation shape
     * the members bound to the document of the request shape for the operation
     * will be returned
     */
    private fun getContentsForShape(shapeId: String): String {
        val ctx = defaultModel.newTestContext()

        val testMembers = when (val shape = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))) {
            is OperationShape -> {
                val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
                val requestBindings = bindingIndex.getRequestBindings(shape)
                val unionShape = ctx.generationCtx.model.expectShape(requestBindings.values.first().member.target)
                unionShape.members().toList()
            }
            is StructureShape -> {
                shape.members().toList()
            }
            else -> throw RuntimeException("unknown conversion for $shapeId")
        }

        return testRender(testMembers) { members, writer ->
            SerializeUnionGenerator2(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
    }

    private fun getContentsForShape(model: Model, shapeId: String): String {
        val ctx = model.newTestContext()

        val testMembers = when (val shape = ctx.generationCtx.model.expectShape(ShapeId.from(shapeId))) {
            is OperationShape -> {
                val bindingIndex = HttpBindingIndex.of(ctx.generationCtx.model)
                val requestBindings = bindingIndex.getRequestBindings(shape)
                requestBindings.values
                    .filter { it.location == HttpBinding.Location.DOCUMENT }
                    .sortedBy { it.memberName }
                    .map { it.member }
            }
            is StructureShape -> {
                shape.members().toList()
            }
            else -> throw RuntimeException("unknown conversion for $shapeId")
        }

        return testRender(testMembers) { members, writer ->
            SerializeUnionGenerator2(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
    }

    @Test
    fun `it serializes a structure containing a list of a primitive type`() {
        val model = (
                modelPrefix + """            
            structure FooRequest { 
                payload: FooUnion
            }
            
            union FooUnion {
                intListVal: IntList,
                strVal: String
            }
            
            list IntList {
                member: Integer
            }
        """
                ).asSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR) {
                        for (c0 in input.payload) {
                            serializeInt(c0)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it handles union request serializer`() {
        val contents = getContentsForShape("com.test#UnionInput")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
serializer.serializeStruct(OBJ_DESCRIPTOR) {
    when (input) {
        is MyUnion.I32 -> field(I32_DESCRIPTOR, input.value)
        is MyUnion.StringA -> field(STRINGA_DESCRIPTOR, input.value)
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles list inputs`() {
        val contents = getContentsForShape("com.test#UnionAggregateInput")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
serializer.serializeStruct(OBJ_DESCRIPTOR) {
    when (input) {
        is MyAggregateUnion.I32 -> field(I32_DESCRIPTOR, input.value)
        is MyAggregateUnion.IntList -> {
            listField(INTLIST_DESCRIPTOR) {
                for(m0 in input.value) {
                    serializeInt(m0)
                }
            }
        }
        is MyAggregateUnion.IntMap -> {
            mapField(INTMAP_DESCRIPTOR) {
                input.value.forEach { (key, value) -> entry(key, value) }
            }
        }
        is MyAggregateUnion.Nested3 -> field(NESTED3_DESCRIPTOR, NestedSerializer(input.value))
        is MyAggregateUnion.Timestamp4 -> field(TIMESTAMP4_DESCRIPTOR, input.value.format(TimestampFormat.ISO_8601))
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }
}
