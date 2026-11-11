/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen

import aws.smithy.kotlin.codegen.test.TestModelDefault
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.toSmithyModel
import io.kotest.matchers.string.shouldContain
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CodegenVisitorTest {
    private fun generate(model: Model): MockManifest {
        val manifest = MockManifest()
        val context = PluginContext.builder()
            .model(model)
            .fileManifest(manifest)
            .settings(
                Node.objectNodeBuilder()
                    .withMember("service", Node.from("com.test#Example"))
                    .withMember(
                        "package",
                        Node.objectNode()
                            .withMember("name", Node.from(TestModelDefault.NAMESPACE))
                            .withMember("version", Node.from(TestModelDefault.MODEL_VERSION)),
                    )
                    .withMember("build", Node.objectNodeBuilder().withMember("rootProject", Node.from(false)).build())
                    .build(),
            )
            .build()
        KotlinCodegenPlugin().execute(context)
        return manifest
    }

    @Test
    fun itRendersOperationOutputsReferencedAsMembersOnce() {
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [GetFoo, GetSummary]
            }
            operation GetFoo {
                output: GetFooResponse
            }
            operation GetSummary {
                output: GetSummaryResponse
            }
            structure GetFooResponse {
                v: String
            }
            structure GetSummaryResponse {
                foo: GetFooResponse
            }
        """.toSmithyModel(applyDefaultTransforms = false)

        val manifest = generate(model)

        manifest.expectFileString("src/main/kotlin/com/test/model/GetFooResponse.kt")
            .shouldContainOnlyOnceWithDiff("public class GetFooResponse private constructor")
        manifest.expectFileString("src/main/kotlin/com/test/model/GetSummaryResponse.kt")
            .shouldContainOnlyOnceWithDiff("public val foo: com.test.model.GetFooResponse? = builder.foo")
    }

    @Test
    fun itFailsWhenShapesGenerateTheSameType() {
        // `foo_bar` and `FooBar` are distinct smithy names but both generate `FooBar`
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }
            operation GetFoo {
                output: GetFooOutput
            }
            structure GetFooOutput {
                a: foo_bar,
                b: FooBar
            }
            structure foo_bar {}
            structure FooBar {}
        """.toSmithyModel(applyDefaultTransforms = false)

        val ex = assertFailsWith<CodegenException> { generate(model) }
        ex.message!!.shouldContain(" * com.test.model.FooBar: com.test#foo_bar, com.test#FooBar")
    }
}
