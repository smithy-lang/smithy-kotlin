/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.utils.CodeWriter

// Determines the jvmTarget version emitted to the build file
private val JVM_TARGET_VERSION: String = System.getProperty("smithy.kotlin.codegen.jvmTargetVersion", "1.8")

fun writeGradleBuild(
    settings: KotlinSettings,
    manifest: FileManifest,
    dependencies: List<KotlinDependency>
) {
    val writer = createCodeWriter()

    val isKmp = settings.build.generateMultiplatformProject
    val isRootModule = settings.build.generateFullProject

    val annotationRenderer: InlineWriter = {
        val annotations = settings.build.optInAnnotations ?: emptyList()
        if (annotations.isNotEmpty()) {
            val formatted = annotations.joinToString(
                separator = ",\n",
                transform = {
                    "\"$it\""
                }
            )

            write(formatted)
        }
    }

    when {
        isKmp && isRootModule -> renderRootKmpGradleBuild(writer, dependencies, annotationRenderer)
        !isKmp && isRootModule -> renderRootJvmGradleBuild(writer, dependencies, annotationRenderer)
        else -> error("")
    }

    val contents = writer.toString()
    manifest.writeFile("build.gradle.kts", contents)
    if (settings.build.generateFullProject) {
        manifest.writeFile("settings.gradle.kts", "")
    }
}

fun renderRootKmpGradleBuild(
    writer: CodeWriter,
    dependencies: List<KotlinDependency>,
    annotationRenderer: InlineWriter
) {

    writer.write("""
        plugins {
            kotlin("multiplatform") version "#L"
        }
        
        repositories {
            mavenLocal()
            mavenCentral()
        }
        
        kotlin {
            jvm {
                compilations.all {
                    kotlinOptions.jvmTarget = "#L"
                }
                testRuns["test"].executionTask.configure {
                    useJUnit()
                }
            }
            sourceSets {
                val commonMain by getting {
                    dependencies {
                        #W
                    }
                }
                val jvmMain by getting
            }
            val optinAnnotations = listOf(
                #W
            )
            kotlin.sourceSets.all {
                optinAnnotations.forEach { languageSettings.optIn(it) }
            }
        }
        """.trimIndent(),
        KOTLIN_COMPILER_VERSION,
        JVM_TARGET_VERSION,
        { w: CodeWriter -> w.dependencyRenderer(isSrcScope = true, isKmp = true, dependencies = dependencies) },
        annotationRenderer
    )
}

fun renderRootJvmGradleBuild(
    writer: CodeWriter,
    dependencies: List<KotlinDependency>,
    annotationRenderer: InlineWriter
) {
    writer.write("""
        plugins {
            kotlin("jvm") version "#L"
        }

        repositories {
            mavenLocal()
            mavenCentral()
        }

        dependencies {
            #W
        }
        val optinAnnotations = listOf(
            #W
        )
        kotlin.sourceSets.all {
            optinAnnotations.forEach { languageSettings.optIn(it) }
        }

        tasks.test {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = true
            }
        }
    """.trimIndent(),
        KOTLIN_COMPILER_VERSION,
        { w: CodeWriter -> w.dependencyRenderer(isSrcScope = true, isKmp = false, dependencies = dependencies) },
        annotationRenderer)
}

private fun CodeWriter.dependencyRenderer(isSrcScope: Boolean, isKmp: Boolean, dependencies: List<KotlinDependency>) {
    if (!isKmp) {
        write("implementation(kotlin(\"stdlib\"))")
    }

    // TODO - can we make kotlin dependencies not specify a version e.g. kotlin("kotlin-test")
    // TODO - Kotlin MPP setup (pass through KotlinSettings) - maybe separate gradle writers
    val orderedDependencies = dependencies.sortedWith(compareBy({ it.config }, { it.artifact }))
    orderedDependencies
        .filter {
            if (isSrcScope) !it.config.isTestScope else it.config.isTestScope
        }
        .forEach { dependency ->
            write("${dependency.config}(\"#L:#L:#L\")", dependency.group, dependency.artifact, dependency.version)
        }
}

private fun createCodeWriter(): CodeWriter =
    CodeWriter().apply {
        trimBlankLines()
        trimTrailingSpaces()
        setIndentText("    ")
        expressionStart = '#'
        putFormatter('W', InlineWriterBiFunction(::createCodeWriter))
    }

/**
 * Create the gradle build file for the generated code
 */
fun writeGradleBuild2(
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

/*

plugins {
    kotlin("jvm") version "1.6.10"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("aws.smithy.kotlin:http:0.7.7-SNAPSHOT")
    implementation("aws.smithy.kotlin:http-client-engine-ktor:0.7.7-SNAPSHOT")
    implementation("aws.smithy.kotlin:serde:0.7.7-SNAPSHOT")
    implementation("aws.smithy.kotlin:utils:0.7.7-SNAPSHOT")
    api("aws.smithy.kotlin:runtime-core:0.7.7-SNAPSHOT")
}
val optinAnnotations = listOf(
    "kotlin.RequiresOptIn",
    "aws.smithy.kotlin.runtime.util.InternalApi"
)
kotlin.sourceSets.all {
    optinAnnotations.forEach { languageSettings.optIn(it) }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}


-------------





 */