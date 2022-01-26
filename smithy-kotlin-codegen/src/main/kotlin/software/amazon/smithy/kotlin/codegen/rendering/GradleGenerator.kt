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

/**
 * Generate Gradle build files
 * @param settings [KotlinSettings] describe how the build files should be generated
 * @param manifest Where the generated files are added
 * @param dependencies list of [KotlinDependency]s for the project
 */
fun writeGradleBuild(
    settings: KotlinSettings,
    manifest: FileManifest,
    dependencies: List<KotlinDependency>
) {
    val writer = createCodeWriter()

    val isKmp = settings.build.generateMultiplatformProject
    val isRootModule = settings.build.generateFullProject

    val annotationRenderer: InlineCodeWriter = {
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

    val pluginsBodyRenderer: InlineCodeWriter = {
        val pluginName = if (isKmp) "multiplatform" else "jvm"

        write(
            "kotlin(\"$pluginName\") #W",
            { w: CodeWriter ->
                if (isRootModule) {
                    w.write("""version "#L"""", KOTLIN_COMPILER_VERSION)
                }
            }
        )
    }

    when {
        isKmp -> renderKmpGradleBuild(
            writer,
            isRootModule,
            dependencies,
            pluginsBodyRenderer,
            repositoryRenderer,
            annotationRenderer
        )
        else -> renderJvmGradleBuild(
            writer,
            isRootModule,
            dependencies,
            pluginsBodyRenderer,
            repositoryRenderer,
            annotationRenderer
        )
    }

    val contents = writer.toString()
    manifest.writeFile("build.gradle.kts", contents)
    if (settings.build.generateFullProject) {
        manifest.writeFile("settings.gradle.kts", "")
    }
}

fun renderKmpGradleBuild(
    writer: CodeWriter,
    isRootModule: Boolean,
    dependencies: List<KotlinDependency>,
    pluginsRenderer: InlineCodeWriter,
    repositoryRenderer: InlineCodeWriter,
    annotationRenderer: InlineCodeWriter
) {
    writer.write(
        """
        plugins {
            #W
        }  
        #W
        
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
            val optInAnnotations = listOf(
                #W
            )
            kotlin.sourceSets.all {
                optInAnnotations.forEach { languageSettings.optIn(it) }
            }
        }
        """.trimIndent(),
        pluginsRenderer,
        { w: CodeWriter -> if (isRootModule) repositoryRenderer(w) },
        JVM_TARGET_VERSION,
        { w: CodeWriter -> renderDependencies(w, isSrcScope = true, isKmp = true, dependencies = dependencies) },
        annotationRenderer
    )
}

fun renderJvmGradleBuild(
    writer: CodeWriter,
    isRootModule: Boolean,
    dependencies: List<KotlinDependency>,
    pluginsRenderer: InlineCodeWriter,
    repositoryRenderer: InlineCodeWriter,
    annotationRenderer: InlineCodeWriter
) {
    writer.write(
        """
        plugins {
            #W
        }
        #W

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
        pluginsRenderer,
        { w: CodeWriter -> if (isRootModule) repositoryRenderer(w) },
        { w: CodeWriter -> renderDependencies(w, isSrcScope = true, isKmp = false, dependencies = dependencies) },
        annotationRenderer
    )
}

private fun renderDependencies(writer: CodeWriter, isSrcScope: Boolean, isKmp: Boolean, dependencies: List<KotlinDependency>) {
    if (!isKmp) {
        writer.write("implementation(kotlin(\"stdlib\"))")
    }

    // TODO - can we make kotlin dependencies not specify a version e.g. kotlin("kotlin-test")
    // TODO - Kotlin MPP setup (pass through KotlinSettings) - maybe separate gradle writers
    val orderedDependencies = dependencies.sortedWith(compareBy({ it.config }, { it.artifact }))
    orderedDependencies
        .filter {
            if (isSrcScope) !it.config.isTestScope else it.config.isTestScope
        }
        .forEach { dependency ->
            writer.write("${dependency.config}(\"#L:#L:#L\")", dependency.group, dependency.artifact, dependency.version)
        }
}

private val repositoryRenderer: InlineCodeWriter = {
    write(
        """
            repositories {
                mavenLocal()
                mavenCentral()
            }
        """.trimIndent()
    )
}

// Create a new [CodeWriter] for Gradle kts files
// FIXME ~ new codewriter should use settings from parent. Support from Smithy is needed
//  for this however.  See https://github.com/awslabs/smithy/issues/1066
private fun createCodeWriter(): CodeWriter =
    CodeWriter().apply {
        trimBlankLines()
        trimTrailingSpaces()
        setIndentText("    ")
        expressionStart = '#'
        putFormatter('W', InlineCodeWriterFormatter(::createCodeWriter))
    }
