/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.test.AwsProtocolModelDeclaration
import aws.smithy.kotlin.codegen.test.codegenCborSerializerForShape
import aws.smithy.kotlin.codegen.test.prependNamespaceAndService
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.stripCodegenPrefix
import aws.smithy.kotlin.codegen.test.toSmithyModel
import kotlin.test.Test

/**
 * Verifies that the CBOR serializer generator emits definite-length arrays/maps (passing the collection `.size`)
 * whenever the element/entry count is statically known, and falls back to indefinite-length encoding (no `.size`
 * argument) only where the collection may be null at the call site (i.e. sparse collection elements).
 *
 * The [size]-carrying calls resolve to `CborSerializer`'s definite-length overloads; calls without a size resolve to
 * the indefinite-length ones, which every other format also uses via the default no-op implementations.
 */
class CborSerializeStructGeneratorTest {
    private val modelPrefix = """
            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                input: FooRequest
            }
    """.prependNamespaceAndService(
        protocol = AwsProtocolModelDeclaration.REST_JSON,
        operations = listOf("Foo"),
    ).trimIndent()

    @Test
    fun `it serializes a list member with a definite length`() {
        val model = (
            modelPrefix + """
            structure FooRequest {
                payload: IntList
            }

            list IntList {
                member: Integer
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR, input.payload.size) {
                        for (el0 in input.payload) {
                            serializeInt(el0)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenCborSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a map member with a definite length`() {
        val model = (
            modelPrefix + """
            structure FooRequest {
                payload: StringMap
            }

            map StringMap {
                key: String,
                value: String
            }
        """
            ).toSmithyModel()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR, input.payload.size) {
                        input.payload.forEach { (key, value) ->
                            entry(key, value)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenCborSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a dense nested list with definite lengths at every level`() {
        val model = (
            modelPrefix + """
            structure FooRequest {
                payload: PrimitiveListList
            }

            list PrimitiveListList {
                member: PrimitiveList
            }

            list PrimitiveList {
                member: Integer
            }
        """
            ).toSmithyModel()

        // outer list is definite (input.payload.size); each non-null nested list is also definite (el0.size)
        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR, input.payload.size) {
                        for (el0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR, el0.size) {
                                for (el1 in el0) {
                                    serializeInt(el1)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenCborSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it falls back to indefinite length for sparse nested list elements`() {
        val model = (
            modelPrefix + """
            structure FooRequest {
                payload: SparsePrimitiveListList
            }

            @sparse
            list SparsePrimitiveListList {
                member: PrimitiveList
            }

            list PrimitiveList {
                member: Integer
            }
        """
            ).toSmithyModel()

        // The outer list stays definite (the collection itself is non-null and each element yields exactly one
        // item), but each nested list element may be null, so it cannot read `.size` and must stay indefinite.
        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    listField(PAYLOAD_DESCRIPTOR, input.payload.size) {
                        for (el0 in input.payload) {
                            serializer.serializeList(PAYLOAD_C0_DESCRIPTOR) {
                                if (el0 != null) {
                                    for (el1 in el0) {
                                        serializeInt(el1)
                                    }
                                } else serializeNull()
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenCborSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it serializes a sparse map with a nested list value using guarded definite lengths`() {
        val model = (
            modelPrefix + """
            structure FooRequest {
                payload: SparseListMap
            }

            @sparse
            map SparseListMap {
                key: String,
                value: PrimitiveList
            }

            list PrimitiveList {
                member: Integer
            }
        """
            ).toSmithyModel()

        // The map itself is definite; the nested list value's `.size` is read only inside the `value != null` guard.
        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                if (input.payload != null) {
                    mapField(PAYLOAD_DESCRIPTOR, input.payload.size) {
                        input.payload.forEach { (key, value) ->
                            if (value != null) {
                                listEntry(key, PAYLOAD_C0_DESCRIPTOR, value.size) {
                                    for (el1 in value) {
                                        serializeInt(el1)
                                    }
                                }
                            } else entry(key, null as String?)
                        }
                    }
                }
            }
        """.trimIndent()

        val actual = codegenCborSerializerForShape(model, "com.test#Foo").stripCodegenPrefix()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }
}
