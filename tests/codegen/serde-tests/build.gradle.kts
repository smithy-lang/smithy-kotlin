/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.codegen.smithyKotlinProjectionSrcDir
import aws.sdk.kotlin.gradle.dsl.skipPublishing

plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
    alias(libs.plugins.aws.kotlin.repo.tools.smithybuild)
}

skipPublishing()

val codegen by configurations.getting
dependencies {
    codegen(project(":codegen:smithy-kotlin-codegen"))
    codegen(project(":tests:codegen:serde-codegen-support"))
    codegen(libs.smithy.cli)
    codegen(libs.smithy.model)
}

tasks.generateSmithyProjections {
    smithyBuildConfigs.set(files("smithy-build.json"))
    inputs.dir(project.layout.projectDirectory.dir("model"))
    listOf("xml", "json").forEach { projectionName ->
        val fromDir = smithyBuild.smithyKotlinProjectionSrcDir(projectionName)
        outputs.dir(fromDir)
    }
    buildClasspath.set(codegen)
}

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")
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

dependencies {
    compileOnly(project(":codegen:smithy-kotlin-codegen"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":runtime:runtime-core"))
    implementation(project(":runtime:serde"))
    implementation(project(":runtime:serde:serde-json"))
    implementation(project(":runtime:serde:serde-xml"))
    implementation(project(":runtime:smithy-test"))

    testImplementation(libs.kotlin.test.junit5)
}

val generatedSrcDir = project.layout.projectDirectory.dir("generated-src/main/kotlin")

abstract class StageGeneratedSourcesTask : DefaultTask() {
    @get:InputDirectory
    abstract val projectionsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedSrcDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun stageGeneratedSources() {
        val projections = listOf("xml", "json")

        projections.forEach { projectionName ->
            val fromDir = projectionsDir.dir("$projectionName/kotlin-codegen/src/main/kotlin")
            logger.info("copying from ${fromDir.get()} to ${generatedSrcDir.get()}")
            fs.copy {
                from(fromDir)
                into(generatedSrcDir)
                include("**/model/*.kt")
                include("**/serde/*.kt")
                exclude("**/auth/*.kt")
                exclude("**/endpoints/**.kt")
                exclude("**/serde/*OperationSerializer.kt")
                exclude("**/serde/*OperationDeserializer.kt")
            }
        }
    }
}

val stageGeneratedSources = tasks.register<StageGeneratedSourcesTask>("stageGeneratedSources") {
    group = "codegen"
    dependsOn(tasks.generateSmithyProjections)
    generatedSrcDir.set(layout.projectDirectory.dir("generated-src/main/kotlin"))
    projectionsDir.set(layout.buildDirectory.dir("smithyprojections/serde-tests"))
}

tasks.kotlinSourcesJar {
    dependsOn(
        tasks.generateSmithyProjections,
        stageGeneratedSources,
    )
}

kotlin.sourceSets.getByName("main") {
    kotlin.srcDir(generatedSrcDir)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(stageGeneratedSources)
}

tasks.clean.configure {
    delete(project.layout.projectDirectory.dir("generated-src"))
}
