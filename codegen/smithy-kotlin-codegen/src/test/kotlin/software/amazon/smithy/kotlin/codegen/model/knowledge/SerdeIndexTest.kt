/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.model.knowledge

import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test
import kotlin.test.assertEquals

class SerdeIndexTest {

    @Test
    fun testFindsNestedStructs() {
        // document serializers and deserializers are generally required for all nested structure and union shapes
        // reachable from a given shape. For operations this is the input shape, for outputs it's the output shape + errors.
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }
            operation GetFoo{
                input: GetFooRequest,
                output: GetFooResponse,
                errors: [ComplexError]
            }
            
            structure GetFooRequest {
                turtle: RequestTurtle,
                intMember: Integer,
                strMember: String,
                intList: IntList,
                intMap: IntMap
            }
            
            structure GetFooResponse{
                turtle: ResponseTurtle,
                intMember: Integer,
                strMember: String
            }
            
            @error("server")
            structure ComplexError{
                intMember: Integer,
                strMember: String,
                nested: ErrorTurtle
            }
            
            list StructureList {
                member: NestedListStruct
            }
            
            list IntList {
                member: Integer
            }
            
            map IntMap{
                key: String,
                value: Integer
            }
            
            map StructMap{
                key: String,
                value: NestedMapStruct
            }
            
            structure ErrorTurtle{ }
            
            structure NestedListStruct {
                turtle: ListTurtle
            }
            
            structure ListTurtle {}
            
            structure NestedMapStruct {
                turtle: MapTurtle
            }
            
            structure MapTurtle {}
            structure RequestTurtle {
                turtle1: StructureList,
                turtle2: StructMap
            }
            
            structure ResponseTurtle {
                union: MyUnion
            }
            
            union MyUnion{
                intMember: Integer,
                strMember: String,
                turtle: UnionTurtle
            }
            
            structure UnionTurtle {}
            
        """.toSmithyModel(applyDefaultTransforms = false)

        val index = SerdeIndex.of(model)

        val fooOperation = model.expectShape(ShapeId.from("com.test#GetFoo"))
        val fooRequest = model.expectShape(ShapeId.from("com.test#GetFooRequest"))
        val fooResponse = model.expectShape(ShapeId.from("com.test#GetFooResponse"))
        val opSerializers = index.requiresDocumentSerializer(fooOperation).map { it.id.name }.toSet()

        val expectedOpSerializerShapes = setOf(
            "RequestTurtle",
            "NestedListStruct",
            "ListTurtle",
            "NestedMapStruct",
            "MapTurtle",
        )

        assertEquals(expectedOpSerializerShapes, opSerializers)

        val requestSerializers = index.requiresDocumentSerializer(fooRequest).map { it.id.name }.toSet()
        assertEquals(expectedOpSerializerShapes, requestSerializers)

        val opDeserializers = index.requiresDocumentDeserializer(fooOperation).map { it.id.name }.toSet()
        val expectedOpDeserializerShapes = setOf(
            "ResponseTurtle",
            "ErrorTurtle",
            "MyUnion",
            "UnionTurtle",
        )

        assertEquals(expectedOpDeserializerShapes, opDeserializers)

        val responseDeserializers = index.requiresDocumentDeserializer(fooResponse).map { it.id.name }.toSet()
        assertEquals(setOf("ResponseTurtle", "MyUnion", "UnionTurtle"), responseDeserializers)
    }
}
