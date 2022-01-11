/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
import software.amazon.smithy.gradle.tasks.SmithyBuild

plugins {
    kotlin("jvm")
    id("software.amazon.smithy")
}

buildscript {
    dependencies {
        val smithyVersion: String by project
        val smithyCliConfig = configurations.maybeCreate("smithyCli")

        classpath("software.amazon.smithy:smithy-cli:$smithyVersion")
        smithyCliConfig("software.amazon.smithy:smithy-cli:$smithyVersion")
    }
}

extra.set("skipPublish", true)

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.util.InternalApi")
kotlin.sourceSets.all {
    optinAnnotations.forEach { languageSettings.optIn(it) }
}

kotlin.sourceSets.getByName("main") {
    kotlin.srcDir("${project.buildDir}/generated-src/src")
    kotlin.srcDir("${project.buildDir}/smithyprojections/paginator-tests/paginator-tests/kotlin-codegen")
}

tasks["smithyBuildJar"].enabled = false

val codegen: Configuration by configurations.creating
val generateSdk = tasks.create<SmithyBuild>("generateSdk") {
    group = "codegen"
    classpath = configurations.getByName("codegen")
    inputs.file(projectDir.resolve("smithy-build.json"))
    inputs.files(configurations.getByName("codegen"))
}

val stageGeneratedSources = tasks.register("stageGeneratedSources") {
    group = "codegen"
    dependsOn(generateSdk)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>{
    dependsOn(stageGeneratedSources)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

dependencies {
    val kotlinVersion: String by project
    val coroutinesVersion: String by project

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    implementation(project(":smithy-kotlin-codegen"))
    implementation(project(":runtime:runtime-core"))

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
}
