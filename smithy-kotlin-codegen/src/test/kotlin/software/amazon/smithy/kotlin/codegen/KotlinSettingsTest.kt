/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId

class KotlinSettingsTest {
    @Test fun `infers default service`() {
        val model = Model.assembler()
            .addImport(KotlinSettingsTest::class.java.getResource("simple-service.smithy"))
            .discoverModels()
            .assemble()
            .unwrap()

        val settings = KotlinSettings.from(model, Node.objectNodeBuilder()
            .withMember("module", Node.from("example"))
            .withMember("moduleVersion", Node.from("1.0.0"))
            .withMember("build", Node.objectNodeBuilder().withMember("rootProject", Node.from(false)).build())
            .build())

        assertEquals(ShapeId.from("smithy.example#Example"), settings.service)
        assertEquals("example", settings.moduleName)
        assertEquals("1.0.0", settings.moduleVersion)
    }

    @Test fun `correctly reads rootProject var from build settings`() {
        val model = Model.assembler()
                .addImport(KotlinSettingsTest::class.java.getResource("simple-service.smithy"))
                .discoverModels()
                .assemble()
                .unwrap()

        val settings = KotlinSettings.from(model, Node.objectNodeBuilder()
                .withMember("module", Node.from("example"))
                .withMember("moduleVersion", Node.from("1.0.0"))
                .withMember("build", Node.objectNodeBuilder().withMember("rootProject", Node.from(true)).build())
                .build())

        assertTrue(settings.build.rootProject)
    }
}
