/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.samples

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.test.newTestContext
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import software.amazon.smithy.kotlin.codegen.test.toCodegenContext
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.transform.ModelTransformer
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KDocSamplesGeneratorTest {
    private val model = """
        namespace com.test
        
        service FooService {
            version: "1.0.0"
            operations: [GetFoo]
        }
        
        @documentation("This is documentation for GetFoo")
        operation GetFoo {
            input: GetFooInput
            output: GetFooOutput
            errors: [MyOperationError]
        }
        
        @error("client")
        structure MyOperationError {
            @required
            message: String
        }
        
        structure GetFooInput { 
            member1: String
        }
        
        structure GetFooOutput { }
        
        apply GetFoo @examples([
            {
                title: "Invoke GetFoo"
                input: {
                    member1: "bar"
                }
                output: {}
            },
            {
                title: "Invoke GetFoo example 2"
                input: {
                    member1: "qux"
                }
                output: {}
            },
            {
                title: "Error example for MyOperation"
                input: {
                    member1: "!"
                }
                error: {
                    shapeId: MyOperationError
                    content: {
                        message: "Invalid 'foo'. Special character not allowed."
                    }
                },
                allowConstraintErrors: true
            }
        ])
        """.toSmithyModel()

    private val settings = KotlinSettings(
        ShapeId.from("com.test#FooService"),
        KotlinSettings.PackageSettings(
            "test",
            "1.0",
            "",
        ),
        "Foo",
    )

    @Test
    fun itPreprocessesDocTraits() {
        val integration = KDocSamplesGenerator()
        val modified = integration.preprocessModel(model, settings)

        val docs = modified.expectShape<OperationShape>("com.test#GetFoo").expectTrait<DocumentationTrait>().value
        val expected = """
            This is documentation for GetFoo
            
            @sample test.samples.GetFoo.sample
            @sample test.samples.GetFoo.sample1
        """.trimIndent()
        assertEquals(expected, docs)
    }

    @Test
    fun itHandlesOperationsWithNoDocTrait() {
        val modelNoDocs = ModelTransformer
            .create()
            .removeTraitsIf(model) { _, trait ->
                trait is DocumentationTrait
            }
            .toBuilder()
            .build()

        assertFalse(modelNoDocs.expectShape<OperationShape>("com.test#GetFoo").hasTrait<DocumentationTrait>())

        val integration = KDocSamplesGenerator()
        val modified = integration.preprocessModel(modelNoDocs, settings)

        val docs = modified.expectShape<OperationShape>("com.test#GetFoo").expectTrait<DocumentationTrait>().value
        val expected = """
            @sample test.samples.GetFoo.sample
            @sample test.samples.GetFoo.sample1
        """.trimIndent()
        assertEquals(expected, docs)
    }

    @Test
    fun testSamplesAreGenerated() {
        val integration = KDocSamplesGenerator()
        val testCtx = model.newTestContext("FooService", settings = settings, integrations = listOf(integration))

        integration.writeAdditionalFiles(testCtx.toCodegenContext(), testCtx.generationCtx.delegator)
        val expectedContents = """
            class GetFoo {

                @Sample
                fun sample() {
                    // Invoke GetFoo
                    val resp = fooClient.getFoo {
                        member1 = "bar"
                    }
                }

                @Sample
                fun sample1() {
                    // Invoke GetFoo example 2
                    val resp = fooClient.getFoo {
                        member1 = "qux"
                    }
                }
            }

        """.trimIndent()

        testCtx.generationCtx.delegator.flushWriters()

        val actualContents = testCtx.manifest.expectFileString("src/samples/GetFoo.kt")
        actualContents.shouldContainOnlyOnceWithDiff(expectedContents)
        actualContents.shouldContainOnlyOnceWithDiff("package test.samples")
    }
}
