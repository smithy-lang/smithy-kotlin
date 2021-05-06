/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.ShapeId

class JsonSerdeDescriptorGeneratorTest {

    @Test
    fun itHandlesSimpleDescriptors() {
        val model = """
                @http(method: "POST", uri: "/foo")
                operation Foo {
                    input: FooRequest
                }  
                
                structure FooRequest { 
                    strVal: String,
                    intVal: Integer
                }
        """.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()

        val testCtx = model.newTestContext()
        val writer = testCtx.newWriter()
        val shape = model.expectShape(ShapeId.from("com.test#FooRequest"))
        val renderingCtx = testCtx.toRenderingContext(writer, shape)

        JsonSerdeDescriptorGenerator(renderingCtx).render()

        val expectedDescriptors = """
                private val INTVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("intVal"))
                private val STRVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("strVal"))
                private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                    field(INTVAL_DESCRIPTOR)
                    field(STRVAL_DESCRIPTOR)
                }
            """.formatForTest("    ")

        val contents = writer.toString()
        contents.shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }

    @Test
    fun itGeneratesNestedDescriptors() {
        val model = """            
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest
            }  
            
            structure FooRequest { 
                payload: BarListList
            }
            
            list BarListList {
                member: BarList
            }
            
            list BarList {
                member: Bar
            }
            
            structure Bar {
                someVal: String
            } 
        """.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()

        val testCtx = model.newTestContext()
        val writer = testCtx.newWriter()
        val shape = model.expectShape(ShapeId.from("com.test#FooRequest"))
        val renderingCtx = testCtx.toRenderingContext(writer, shape)

        JsonSerdeDescriptorGenerator(renderingCtx).render()

        val expectedDescriptors = """
    private val PAYLOAD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, JsonSerialName("payload"))
    private val PAYLOAD_C0_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List)
    private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        field(PAYLOAD_DESCRIPTOR)
    }
    """.formatForTest("    ")

        val contents = writer.toString()
        contents.shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }
}
