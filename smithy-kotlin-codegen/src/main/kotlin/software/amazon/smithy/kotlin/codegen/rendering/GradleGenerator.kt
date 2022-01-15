/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.utils.CodeWriter

/**
 * Create the gradle build file for the generated code
 */
fun writeGradleBuild(
    settings: KotlinSettings,
    manifest: FileManifest,
    dependencies: List<KotlinDependency>
) {
    val writer = CodeWriter().apply {
        trimBlankLines()
        trimTrailingSpaces()
        setIndentText("    ")
        expressionStart = '#'
    }

    val isKmp = settings.build.generateMultiplatformProject

    writer.withBlock("plugins {", "}\n") {
        val projectModuleType = when (isKmp) {
            true -> "multiplatform"
            false -> "jvm"
        }
        if (settings.build.generateFullProject) {
            write("kotlin(\"$projectModuleType\") version #S", KOTLIN_COMPILER_VERSION)
        } else {
            write("kotlin(\"$projectModuleType\")")
        }
    }

    if (settings.build.generateFullProject) {
        writer.withBlock("repositories {", "}\n") {
            write("mavenLocal()")
            write("mavenCentral()")
        }
    }

    if (isKmp) { // In KMP projects the following generated code lives within a 'kotlin' block.
        writer.withBlock("kotlin {", "}") {
            withBlock("jvm {", "}") {
                withBlock("compilations.all {", "}") {
                    write("""kotlinOptions.jvmTarget = "$JVM_TARGET_VERSION"""")
                }
                withBlock("testRuns[\"test\"].executionTask.configure {", "}") {
                    write("useJUnit()")
                }
            }
            withBlock("sourceSets {", "}") {
                withBlock("val commonMain by getting {", "}") {
                    renderDependencies(writer, dependencies, isKmp) { !it.config.isTestScope }
                }
                if (dependencies.any { it.config.isTestScope }) {
                    withBlock("val commonTest by getting {", "}") {
                        renderDependencies(writer, dependencies, isKmp) { it.config.isTestScope }
                    }
                }
                write("val jvmMain by getting")
            }
            renderAnnotations(writer, settings.build.optInAnnotations ?: emptyList())
        }
    } else {
        renderDependencies(writer, dependencies, isKmp)
        renderAnnotations(writer, settings.build.optInAnnotations ?: emptyList())
    }

    if (!isKmp) {
        writer
            .write("")
            .withBlock("tasks.test {", "}") {
                write("useJUnitPlatform()")
                withBlock("testLogging {", "}") {
                    write("""events("passed", "skipped", "failed")""")
                    write("showStandardStreams = true")
                }
            }
    }

    val contents = writer.toString()
    manifest.writeFile("build.gradle.kts", contents)
    if (settings.build.generateFullProject) {
        manifest.writeFile("settings.gradle.kts", "")
    }
}

fun renderAnnotations(writer: CodeWriter, annotations: List<String>) {
    if (annotations.isNotEmpty()) {
        writer.withBlock("val optinAnnotations = listOf(", ")") {
            call {
                val formatted = annotations.joinToString(
                    separator = ",\n",
                    transform = {
                        "\"$it\""
                    }
                )

                writer.write(formatted)
            }
        }

        writer.withBlock("kotlin.sourceSets.all {", "}") {
            write("optinAnnotations.forEach { languageSettings.optIn(it) } ")
        }
    }
}

fun renderDependencies(
    writer: CodeWriter,
    dependencies: List<KotlinDependency>,
    isKmp: Boolean,
    filter: (KotlinDependency) -> Boolean = { true }
) {
    writer.withBlock("dependencies {", "}") {
        if (!isKmp) {
            write("implementation(kotlin(\"stdlib\"))")
        }

        // TODO - can we make kotlin dependencies not specify a version e.g. kotlin("kotlin-test")
        // TODO - Kotlin MPP setup (pass through KotlinSettings) - maybe separate gradle writers
        val orderedDependencies = dependencies.sortedWith(compareBy({ it.config }, { it.artifact }))
        orderedDependencies
            .filter(filter)
            .forEach { dependency ->
                write("${dependency.config}(\"#L:#L:#L\")", dependency.group, dependency.artifact, dependency.version)
            }
    }
}
