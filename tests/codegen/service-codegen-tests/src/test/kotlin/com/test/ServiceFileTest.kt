/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class ServiceFileTest {
    val packageName = "com.test"
    val packagePath = packageName.replace('.', '/')

    val projectDir: Path = Paths.get("build/generated-service")

    @Test
    fun `generates service and all necessary files`() {
        assertTrue(projectDir.resolve("build.gradle.kts").exists())
        assertTrue(projectDir.resolve("settings.gradle.kts").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/Main.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/Routing.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/config/ServiceFrameworkConfig.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/framework/ServiceFramework.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/plugins/ContentTypeGuard.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/plugins/ErrorHandler.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/utils/Logging.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/auth/Authentication.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/auth/Validation.kt").exists())

        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/model/PostTestRequest.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/model/PostTestResponse.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/serde/PostTestOperationSerializer.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/serde/PostTestOperationDeserializer.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/operations/PostTestOperation.kt").exists())
    }
}
