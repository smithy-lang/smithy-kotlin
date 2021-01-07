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
import software.amazon.smithy.kotlin.codegen.integration.SerializeStructGenerator2
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.TimestampFormatTrait
import java.lang.RuntimeException

class SerializeStructGeneratorTest {
    private val defaultModel: Model = javaClass.getResource("http-binding-protocol-generator-test.smithy").asSmithy()

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
            SerializeStructGenerator(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }
    }

    @Test
    fun `it handles smoke test request serializer`() {
        val contents = getContentsForShape("com.test#SmokeTest")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payload1?.let { field(PAYLOAD1_DESCRIPTOR, it) }
                input.payload2?.let { field(PAYLOAD2_DESCRIPTOR, it) }
                input.payload3?.let { field(PAYLOAD3_DESCRIPTOR, NestedSerializer(it)) }
            }
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles union request serializer`() {
        val contents = getContentsForShape("com.test#UnionInput")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.payloadUnion?.let { field(PAYLOADUNION_DESCRIPTOR, MyUnionSerializer(it)) }
            }
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles list inputs`() {
        val contents = getContentsForShape("com.test#ListInput")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.blobList != null) {
                    listField(BLOBLIST_DESCRIPTOR) {
                        for(m0 in input.blobList) {
                            serializeString(m0.encodeBase64String())
                        }
                    }
                }
                if (input.enumList != null) {
                    listField(ENUMLIST_DESCRIPTOR) {
                        for(m0 in input.enumList) {
                            serializeString(m0.value)
                        }
                    }
                }
                if (input.intList != null) {
                    listField(INTLIST_DESCRIPTOR) {
                        for(m0 in input.intList) {
                            serializeInt(m0)
                        }
                    }
                }
                if (input.nestedIntList != null) {
                    listField(NESTEDINTLIST_DESCRIPTOR) {
                        for(m0 in input.nestedIntList) {
                            serializer.serializeList(NESTEDINTLIST_C0_DESCRIPTOR) {
                                for(m1 in m0) {
                                    serializeInt(m1)
                                }
                            }
                        }
                    }
                }
                if (input.nestedMapList != null) {
                    listField(NESTEDMAPLIST_DESCRIPTOR) {
                        for(m0 in input.nestedMapList) {
                            if (m0 != null) {
                                mapField(NESTEDMAPLIST_C0_DESCRIPTOR) {
                                    m0.forEach { (key, value) -> entry(key, value) }
                                }
                            }
                        }
                    }
                }
                if (input.structList != null) {
                    listField(STRUCTLIST_DESCRIPTOR) {
                        for(m0 in input.structList) {
                            serializeSdkSerializable(NestedSerializer(m0))
                        }
                    }
                }
            }
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles maps`() {
        val contents = getContentsForShape("com.test#MapInput")
        contents.shouldSyntacticSanityCheck()

        val expectedContents = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.blobMap != null) {
                    mapField(BLOBMAP_DESCRIPTOR) {
                        input.blobMap.forEach { (key, value) -> entry(key, value.encodeBase64String()) }
                    }
                }
                if (input.enumMap != null) {
                    mapField(ENUMMAP_DESCRIPTOR) {
                        input.enumMap.forEach { (key, value) -> entry(key, value.value) }
                    }
                }
                if (input.intMap != null) {
                    mapField(INTMAP_DESCRIPTOR) {
                        input.intMap.forEach { (key, value) -> entry(key, value) }
                    }
                }
                if (input.mapOfLists != null) {
                    mapField(MAPOFLISTS_DESCRIPTOR) {
                        input.mapOfLists.forEach { (key, value) -> listEntry(key, MAPOFLISTS_C0_DESCRIPTOR) {
                            for(m1 in value ?: emptyList()) {
                                serializeInt(m1)
                            }
                        }}
                    }
                }
                if (input.nestedMap != null) {
                    mapField(NESTEDMAP_DESCRIPTOR) {
                        input.nestedMap.forEach { (key, value) -> mapEntry(key, NESTEDMAP_C0_DESCRIPTOR) {
                            if (key != null) {
                                mapField(KEY_DESCRIPTOR) {
                                    key.forEach { (key, value) -> entry(key, value) }
                                }
                            }
                        }}
                    }
                }
                if (input.structMap != null) {
                    mapField(STRUCTMAP_DESCRIPTOR) {
                        input.structMap.forEach { (key, value) -> entry(key, if (value != null) ReachableOnlyThroughMapSerializer(value) else null) }
                    }
                }
            }
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it serializes enums as raw values`() {
        val contents = getContentsForShape("com.test#NestedEnum")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.myEnum?.let { field(MYENUM_DESCRIPTOR, it.value) }
            }
        """.trimIndent()
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes sparse lists`() {
        val expectedContents = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.sparseIntList != null) {
                    listField(SPARSEINTLIST_DESCRIPTOR) {
                        for(m0 in input.sparseIntList) {
                            if (m0 != null) serializeInt(m0) else serializeNull()
                        }
                    }
                }
            }
        """.trimIndent()

        val ctx = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

            @http(method: "POST", uri: "/input/list")
            operation GetFoo {
                input: GetFooInput
            }
            
            @sparse
            list SparseIntList {
                member: Integer
            }
            
            structure GetFooInput {
                sparseIntList: SparseIntList
            }
        """.trimIndent()
            .asSmithyModel()
            .newTestContext()

        val op = ctx.expectShape("com.test#GetFoo")

        val contents = testRender(ctx.requestMembers(op)) { members, writer ->
            SerializeStructGenerator2(
                ctx.generationCtx,
                members,
                writer,
                TimestampFormatTrait.Format.EPOCH_SECONDS
            ).render()
        }

        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }
}
