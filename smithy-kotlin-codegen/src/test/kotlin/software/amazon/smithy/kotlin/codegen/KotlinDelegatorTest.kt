/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.kotlin.codegen.test.TestDefault
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.node.Node

class KotlinDelegatorTest {
    @Test fun `it renders files into namespace`() {
        val model = javaClass.getResource("simple-service-with-operation.smithy").toSmithyModel()

        val manifest = MockManifest()
        val context = PluginContext.builder()
            .model(model)
            .fileManifest(manifest)
            .settings(
                Node.objectNodeBuilder()
                    .withMember("service", Node.from(TestDefault.SERVICE_SHAPE_ID))
                    .withMember(
                        "package",
                        Node.objectNode()
                            .withMember("name", Node.from(TestDefault.NAMESPACE))
                            .withMember("version", Node.from(TestDefault.MODEL_VERSION))
                    )
                    .withMember("build", Node.objectNodeBuilder().withMember("rootProject", Node.from(false)).build())
                    .build()
            )
            .build()

        KotlinCodegenPlugin().execute(context)

        // inputs and outputs are renamed. See OperationNormalizer
        Assertions.assertTrue(manifest.hasFile("src/main/kotlin/com/test/model/GetFooRequest.kt"))
        Assertions.assertTrue(manifest.hasFile("src/main/kotlin/com/test/model/GetFooResponse.kt"))
        Assertions.assertTrue(manifest.hasFile("src/main/kotlin/com/test/TestClient.kt"))
    }

    @Test fun `it adds imports`() {
        val model = javaClass.getResource("simple-service-with-operation.smithy").toSmithyModel()

        val manifest = MockManifest()
        val context = PluginContext.builder()
            .model(model)
            .fileManifest(manifest)
            .settings(
                Node.objectNodeBuilder()
                    .withMember("service", Node.from(TestDefault.SERVICE_SHAPE_ID))
                    .withMember(
                        "package",
                        Node.objectNode()
                            .withMember("name", Node.from(TestDefault.NAMESPACE))
                            .withMember("version", Node.from(TestDefault.MODEL_VERSION))
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
}
