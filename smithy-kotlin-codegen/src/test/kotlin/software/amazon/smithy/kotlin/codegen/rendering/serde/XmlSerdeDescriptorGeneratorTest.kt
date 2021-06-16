/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.ShapeId

class XmlSerdeDescriptorGeneratorTest {

    fun getContents(modelSnippet: String, shapeName: String): String {
        val model = modelSnippet.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()

        val testCtx = model.newTestContext()
        val writer = testCtx.newWriter()
        val shape = model.expectShape(ShapeId.from("com.test#$shapeName"))
        val renderingCtx = testCtx.toRenderingContext(writer, shape)

        XmlSerdeDescriptorGenerator(renderingCtx).render()
        return writer.toString()
    }

    @Test
    fun `it generates field descriptors for simple structures`() {
        val snippet = """
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest,
                output: FooRequest
            }  
            
            structure FooRequest { 
                strVal: String,
                intVal: Integer
            }
        """

        val contents = getContents(snippet, "FooRequest")

        val expectedDescriptors = """
            val INTVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("intVal"))
            val STRVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("strVal"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("FooRequest"))
                field(INTVAL_DESCRIPTOR)
                field(STRVAL_DESCRIPTOR)
            }
        """.formatForTest("")

        contents.shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }

    @Test
    fun `it generates nested field descriptors`() {
        val snippet = """            
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest,
                output: FooRequest
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
        """

        val expectedOperationDescriptors = """
            val PAYLOAD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("payload"))
            val PAYLOAD_C0_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("member"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("FooRequest"))
                field(PAYLOAD_DESCRIPTOR)
            }
        """.formatForTest("")

        val operationContents = getContents(snippet, "FooRequest")
        operationContents.shouldContainOnlyOnceWithDiff(expectedOperationDescriptors)

        val expectedDocumentDescriptors = """
            val SOMEVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("someVal"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("Bar"))
                field(SOMEVAL_DESCRIPTOR)
            }
        """.formatForTest("")
        val documentContents = getContents(snippet, "Bar")
        documentContents.shouldContainOnlyOnceWithDiff(expectedDocumentDescriptors)
    }

    @Test
    fun `it generates field descriptors for nested unions`() {
        val snippet = """
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest,
                output: FooRequest
            }   
                 
            structure FooRequest { 
                payload: FooUnion
            }
            
            union FooUnion {
                structList: BarList
            }
            
            list BarList {
                member: BarStruct
            }
            
            structure BarStruct {
                someValue: FooUnion
            }
        """

        val expectedDescriptors = """
            val STRUCTLIST_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("structList"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("FooUnion"))
                field(STRUCTLIST_DESCRIPTOR)
            }
        """.formatForTest("")

        val contents = getContents(snippet, "FooUnion")
        contents.shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }

    @Test
    fun `it generates expected import declarations`() {
        val snippet = """
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest,
                output: FooRequest
            }   
                 
            @xmlName("CustomFooRequest")
            structure FooRequest {
                @xmlAttribute
                payload: String,
                @xmlFlattened
                listVal: ListOfString
            }
                        
            list ListOfString {
                member: String
            }
        """

        val expected = """
            import software.aws.clientrt.serde.SdkFieldDescriptor
            import software.aws.clientrt.serde.SdkObjectDescriptor
            import software.aws.clientrt.serde.SerialKind
            import software.aws.clientrt.serde.asSdkSerializable
            import software.aws.clientrt.serde.deserializeList
            import software.aws.clientrt.serde.deserializeMap
            import software.aws.clientrt.serde.deserializeStruct
            import software.aws.clientrt.serde.field
            import software.aws.clientrt.serde.serializeList
            import software.aws.clientrt.serde.serializeMap
            import software.aws.clientrt.serde.serializeStruct
            import software.aws.clientrt.serde.xml.Flattened
            import software.aws.clientrt.serde.xml.XmlAttribute
            import software.aws.clientrt.serde.xml.XmlDeserializer
            import software.aws.clientrt.serde.xml.XmlSerialName
        """.formatForTest("")

        val contents = getContents(snippet, "FooRequest")
        contents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it generates field descriptors for flattened xml trait and object descriptor for XmlName trait`() {
        val snippet = """
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest,
                output: FooRequest
            }  
            
            @xmlName("CustomFooRequest")
            structure FooRequest {
                @xmlFlattened
                listVal: ListOfString,
                @xmlFlattened
                mapVal: MapOfInteger
            }
            
            list ListOfString {
                member: String
            }
            
            map MapOfInteger {
                key: String,
                value: String
            }
        """

        val expectedDescriptors = """
            val LISTVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("listVal"), Flattened)
            val MAPVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("mapVal"), Flattened)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("CustomFooRequest"))
                field(LISTVAL_DESCRIPTOR)
                field(MAPVAL_DESCRIPTOR)
            }
        """.formatForTest("")

        getContents(snippet, "FooRequest").shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }

    @Test
    fun `it generates field descriptors for xml attributes and namespace`() {
        val snippet = """
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest,
                output: FooRequest
            }  
            
            @xmlNamespace(uri: "http://foo.com", prefix: "baz")
            structure FooRequest {
                @xmlAttribute
                strVal: String,
                @xmlAttribute
                @xmlName("baz:notIntVal")
                intVal: Integer
            }
        """

        val expectedDescriptors = """
            val INTVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("baz:notIntVal"), XmlAttribute)
            val STRVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("strVal"), XmlAttribute)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("FooRequest"))
                trait(XmlNamespace("http://foo.com", "baz"))
                field(INTVAL_DESCRIPTOR)
                field(STRVAL_DESCRIPTOR)
            }
        """.formatForTest("")

        getContents(snippet, "FooRequest").shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }

    @Test
    fun `it generates field descriptors for renamed maps and lists`() {
        val snippet = """
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest
            }  
            
            structure FooRequest {
                listVal: ListOfString,
                mapVal: MapOfInteger
            }
            
            list ListOfString {
                @xmlName("item")
                member: String
            }
            
            map MapOfInteger {
                @xmlName("baz")
                key: String,
                @xmlName("qux")
                value: String
            }
        """

        val expectedDescriptors = """
            val LISTVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("listVal"), XmlCollectionName("item"))
            val MAPVAL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("mapVal"), XmlMapName(key = "baz", value = "qux"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("FooRequest"))
                field(LISTVAL_DESCRIPTOR)
                field(MAPVAL_DESCRIPTOR)
            }
        """.formatForTest("")

        getContents(snippet, "FooRequest").shouldContainOnlyOnceWithDiff(expectedDescriptors)
    }
}
