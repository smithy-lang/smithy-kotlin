/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test

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
                val INTVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("intVal"))
                val STRVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("strVal"))
                val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                    field(INTVAL_DESCRIPTOR)
                    field(STRVAL_DESCRIPTOR)
                }
            """.formatForTest("")

        val contents = writer.toString()
        contents.shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }

    @Test
    fun itHandlesSimpleUnionDescriptors() {
        val model = """
                @http(method: "POST", uri: "/foo")
                operation Foo {
                    input: FooRequest
                }  
                
                structure FooRequest { 
                    strVal: String,
                    intVal: Integer
                }
                
                union FooResponse {
                    x: String,
                    y: String
                }
        """.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()

        val testCtx = model.newTestContext()
        val writer = testCtx.newWriter()
        val shape = model.expectShape(ShapeId.from("com.test#FooResponse"))
        val renderingCtx = testCtx.toRenderingContext(writer, shape)

        JsonSerdeDescriptorGenerator(renderingCtx).render()

        val expectedDescriptors = """
            val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("x"))
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("y"))
            val __TYPE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("__type"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
                field(__TYPE_DESCRIPTOR)
            }
            """.formatForTest("")

        val contents = writer.toString()
        contents.shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }

    @Test
    fun `it handles union descriptors with '__type'`() {
        val model = """
                @http(method: "POST", uri: "/foo")
                operation Foo {
                    input: FooRequest
                }  
                
                structure FooRequest { 
                    strVal: String,
                    intVal: Integer
                }
                
                union FooResponse {
                    x: String,
                    y: String,
                    __type: String
                }
        """.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()

        val testCtx = model.newTestContext()
        val writer = testCtx.newWriter()
        val shape = model.expectShape(ShapeId.from("com.test#FooResponse"))
        val renderingCtx = testCtx.toRenderingContext(writer, shape)

        JsonSerdeDescriptorGenerator(renderingCtx).render()

        val expectedDescriptors = """
            val TYPE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("__type"))
            val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("x"))
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("y"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                field(TYPE_DESCRIPTOR)
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
            }
            """.formatForTest("")

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
    val PAYLOAD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, JsonSerialName("payload"))
    val PAYLOAD_C0_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List)
    val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        field(PAYLOAD_DESCRIPTOR)
    }
    """.formatForTest("")

        val contents = writer.toString()
        contents.shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }
}
