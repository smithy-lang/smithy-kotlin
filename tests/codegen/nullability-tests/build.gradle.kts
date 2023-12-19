/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import software.amazon.smithy.gradle.tasks.SmithyBuild
import software.amazon.smithy.gradle.tasks.Validate as SmithyValidate

plugins {
    kotlin("jvm")
    alias(libs.plugins.smithy.gradle)
}

skipPublishing()

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")
kotlin.sourceSets.all {
    optinAnnotations.forEach { languageSettings.optIn(it) }
}

val projectionDirs = listOf(
    "smithyprojections/nullability-tests/client-mode/kotlin-codegen",
    "smithyprojections/nullability-tests/client-careful-mode/kotlin-codegen",
)

kotlin.sourceSets.getByName("main") {
    projectionDirs.forEach {
        kotlin.srcDir(layout.buildDirectory.dir(it))
    }
}
tasks.withType<KotlinCompile> {
    // generated code has warnings unfortunately
    kotlinOptions.allWarningsAsErrors = false
}

tasks["smithyBuildJar"].enabled = false

val codegen by configurations.creating
dependencies {
    codegen(project(":codegen:smithy-kotlin-codegen"))
    codegen(libs.smithy.cli)
}

val generateSdk = tasks.register<SmithyBuild>("generateSdk") {
    group = "codegen"
    classpath = codegen
    inputs.file(projectDir.resolve("smithy-build.json"))
    inputs.files(codegen)
}

tasks.named<SmithyValidate>("smithyValidate") {
    classpath = codegen
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateSdk)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    compileOnly(project(":codegen:smithy-kotlin-codegen"))
    implementation(project(":runtime:runtime-core"))
    implementation(project(":runtime:smithy-client"))
    implementation(project(":runtime:protocol:http-client"))
    implementation(project(":runtime:observability:telemetry-api"))
    implementation(project(":runtime:observability:telemetry-defaults"))
    testImplementation(libs.kotlin.test.junit5)
}
