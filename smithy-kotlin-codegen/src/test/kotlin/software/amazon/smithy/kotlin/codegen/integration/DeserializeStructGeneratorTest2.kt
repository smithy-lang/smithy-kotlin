/*
 *
 *  * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *  * SPDX-License-Identifier: Apache-2.0.
 *
 */

package software.amazon.smithy.kotlin.codegen.integration

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

class DeserializeStructGeneratorTest2 {
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
                output: FooResponse
            }        
    """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double"/*, "BigInteger", "BigDecimal"*/])
    // TODO ~ Support BigInteger and BigDecimal Types
    fun `it serializes a structure with a simple fields`(memberType: String) {
        val model = (
                modelPrefix + """            
            structure FooResponse { 
                payload: $memberType
            }
        """
                ).asSmithyModel()

        val expected = """
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while(true) {
                    when(findNextFieldIndex()) {
                        PAYLOAD_DESCRIPTOR.index -> builder.payload = deserialize$memberType()
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
        """.trimIndent()

        val actual = getContentsForShape(model, "com.test#Foo")

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    private fun getContentsForShape(model: Model, shapeId: String): String {
        val ctx = model.newTestContext()
        val op = ctx.expectShape("com.test#Foo")

        return testRender(ctx.responseMembers(op)) { members, writer ->
            DeserializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
    }
}