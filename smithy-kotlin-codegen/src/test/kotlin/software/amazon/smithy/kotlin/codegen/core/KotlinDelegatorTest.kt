/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.core

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.test.TestModelDefault
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import software.amazon.smithy.kotlin.codegen.test.shouldContainWithDiff
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.StructureShape
import kotlin.test.Test
import kotlin.test.assertTrue

class KotlinDelegatorTest {
    @Test fun `it renders files into namespace`() {
        val model = loadModelFromResource("simple-service-with-operation.smithy")

        val manifest = MockManifest()
        val context = PluginContext.builder()
            .model(model)
            .fileManifest(manifest)
            .settings(
                Node.objectNodeBuilder()
                    .withMember("service", Node.from(TestModelDefault.SERVICE_SHAPE_ID))
                    .withMember(
                        "package",
                        Node.objectNode()
                            .withMember("name", Node.from(TestModelDefault.NAMESPACE))
                            .withMember("version", Node.from(TestModelDefault.MODEL_VERSION))
                    )
                    .withMember("build", Node.objectNodeBuilder().withMember("rootProject", Node.from(false)).build())
                    .build()
            )
            .build()

        KotlinCodegenPlugin().execute(context)

        // inputs and outputs are renamed. See OperationNormalizer
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/model/GetFooRequest.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/model/GetFooResponse.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/TestClient.kt"))
    }

    @Test fun `it adds imports`() {
        val model = loadModelFromResource("simple-service-with-operation.smithy")

        val manifest = MockManifest()
        val context = PluginContext.builder()
            .model(model)
            .fileManifest(manifest)
            .settings(
                Node.objectNodeBuilder()
                    .withMember("service", Node.from(TestModelDefault.SERVICE_SHAPE_ID))
                    .withMember(
                        "package",
                        Node.objectNode()
                            .withMember("name", Node.from(TestModelDefault.NAMESPACE))
                            .withMember("version", Node.from(TestModelDefault.MODEL_VERSION))
                    )
                    .withMember("build", Node.objectNodeBuilder().withMember("rootProject", Node.from(false)).build())
                    .build()
            )
            .build()

        KotlinCodegenPlugin().execute(context)

        val contents = manifest.getFileString("src/main/kotlin/com/test/model/GetFooRequest.kt").get()
        contents.shouldContain("import java.math.BigInteger")
        // ensure symbol wasn't imported as an alias by default
        contents.shouldNotContain("as BigInteger")
    }

    @Test
    fun itRendersGeneratedDependencies() {
        val model = loadModelFromResource("simple-service-with-operation.smithy")
        val configContents = """
            {
                "package": {
                    "name": "com.test.example",
                    "version": "1.0.0"
                }
            }
        """.trimIndent()

        val settings = KotlinSettings.from(
            model,
            Node.parse(configContents).expectObjectNode()
        )
        val manifest = MockManifest()
        val delegator = KotlinDelegator(settings, model, manifest, KotlinSymbolProvider(model, settings))

        val generatedSymbol = buildSymbol {
            name = "Foo"
            definitionFile = "FooGenerated.kt"
            namespace = "com.test.example.generated"
            renderBy = { writer ->
                writer.write("hello from generated dep!")
                writer.write("we generated a #identifier.name:L")
            }
        }

        val fooInputShape = model.expectShape<StructureShape>("com.test#GetFooInput")
        delegator.useShapeWriter(fooInputShape) {
            it.write("use generated #T", generatedSymbol)
        }

        val fooOutputShape = model.expectShape<StructureShape>("com.test#GetFooOutput")
        delegator.useShapeWriter(fooOutputShape) {
            it.write("second use of generated #T", generatedSymbol)
        }

        delegator.finalize()
        delegator.flushWriters()

        val generatedSymbolContents = manifest.expectFileString("src/main/kotlin/com/test/example/generated/FooGenerated.kt")
        val fooContents = manifest.expectFileString("src/main/kotlin/com/test/example/model/GetFooInput.kt")

        // should contain the import for Foo just by using it and the symbol
        fooContents.shouldContainWithDiff("import com.test.example.generated.Foo")
        fooContents.shouldContainWithDiff("use generated Foo")

        generatedSymbolContents.shouldContainOnlyOnceWithDiff("hello from generated dep!")
        generatedSymbolContents.shouldContainOnlyOnceWithDiff("we generated a Foo")
    }
}
