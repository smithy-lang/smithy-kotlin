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

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.utils.CodeWriter

/**
 * Create the gradle build file for the generated code
 */
fun writeGradleBuild(settings: KotlinSettings, manifest: FileManifest, dependencies: List<KotlinDependency>) {
    val writer = CodeWriter().apply {
        trimBlankLines()
        trimTrailingSpaces()
        setIndentText("    ")
    }

    writer.withBlock("plugins {", "}\n") {
        if (settings.build.rootProject) {
            write("kotlin(\"jvm\") version \"1.3.72\"")
        } else {
            write("kotlin(\"jvm\")")
        }
    }

    if (settings.build.rootProject) {
        writer.withBlock("repositories {", "}\n") {
            write("mavenLocal()")
            write("mavenCentral()")
            write("jcenter()")
        }
    }

    writer.withBlock("dependencies {", "}\n") {
        write("implementation(kotlin(\"stdlib\"))")
        // TODO - order and group dependencies by their type "implementation", "testImplementation", etc
        // TODO - can we make kotlin dependencies not specify a version e.g. kotlin("kotlin-test")
        // TODO - Kotlin MPP setup (pass through KotlinSettings) - maybe separate gradle writers
        for (dependency in dependencies) {
            write("${dependency.config}(\"\$L:\$L:\$L\")", dependency.group, dependency.artifact, dependency.version)
        }
    }

    writer.write("")
        .openBlock("tasks.test {")
        .write("useJUnitPlatform()")
        .openBlock("testLogging {")
        .write("""events("passed", "skipped", "failed")""")
        .write("showStandardStreams = true")
        .closeBlock("}")
        .closeBlock("}")

    val contents = writer.toString()
    manifest.writeFile("build.gradle.kts", contents)
    if (settings.build.rootProject) {
        manifest.writeFile("settings.gradle.kts", "")
    }
}
