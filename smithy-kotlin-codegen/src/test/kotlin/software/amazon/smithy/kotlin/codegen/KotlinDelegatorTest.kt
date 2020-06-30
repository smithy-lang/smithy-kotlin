/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node

class KotlinDelegatorTest {
    @Test fun `it renders files into namespace`() {
        val model = Model.assembler()
            .addImport(javaClass.getResource("simple-service-with-operation.smithy"))
            .discoverModels()
            .assemble()
            .unwrap()

        val manifest = MockManifest()
        val context = PluginContext.builder()
            .model(model)
            .fileManifest(manifest)
            .settings(Node.objectNodeBuilder()
                .withMember("service", Node.from("smithy.example#Example"))
                .withMember("module", Node.from("example"))
                .withMember("moduleVersion", Node.from("0.1.0"))
                .build())
            .build()

        KotlinCodegenPlugin().execute(context)

        Assertions.assertTrue(manifest.hasFile("src/main/kotlin/example/model/GetFooInput.kt"))
        Assertions.assertTrue(manifest.hasFile("src/main/kotlin/example/model/GetFooOutput.kt"))
        Assertions.assertTrue(manifest.hasFile("src/main/kotlin/example/ExampleClient.kt"))
    }

    @Test fun `it adds imports`() {
        val model = Model.assembler()
            .addImport(javaClass.getResource("simple-service-with-operation.smithy"))
            .discoverModels()
            .assemble()
            .unwrap()

        val manifest = MockManifest()
        val context = PluginContext.builder()
            .model(model)
            .fileManifest(manifest)
            .settings(
                Node.objectNodeBuilder()
                .withMember("service", Node.from("smithy.example#Example"))
                .withMember("module", Node.from("example"))
                .withMember("moduleVersion", Node.from("0.1.0"))
                .build())
            .build()

        KotlinCodegenPlugin().execute(context)

        val contents = manifest.getFileString("src/main/kotlin/example/model/GetFooInput.kt").get()
        contents.shouldContain("import java.math.BigInteger")
        // ensure symbol wasn't imported as an alias by default
        contents.shouldNotContain("as BigInteger")
    }
}
