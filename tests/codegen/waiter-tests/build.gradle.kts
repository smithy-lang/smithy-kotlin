/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
import software.amazon.smithy.gradle.tasks.SmithyBuild
import software.amazon.smithy.gradle.tasks.Validate as SmithyValidate

plugins {
    kotlin("jvm")
    @Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once https://youtrack.jetbrains.com/issue/KTIJ-19369 is fixed
    alias(libs.plugins.smithy.gradle)
}

skipPublishing()

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")
kotlin.sourceSets.all {
    optinAnnotations.forEach { languageSettings.optIn(it) }
}

kotlin.sourceSets.getByName("main") {
    kotlin.srcDir("${project.buildDir}/smithyprojections/waiter-tests/waiter-tests/kotlin-codegen/src/main/kotlin")
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
    kotlinOptions {
        allWarningsAsErrors = false // FIXME Generated waiters code contains lots of warnings
    }
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

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test.junit5)
}
