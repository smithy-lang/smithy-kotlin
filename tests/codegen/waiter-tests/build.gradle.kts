/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
import software.amazon.smithy.gradle.tasks.SmithyBuildTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.smithy.gradle.base)
}

skipPublishing()

smithy {
    format.set(false)
}

val codegen by configurations.creating
dependencies {
    codegen(project(":codegen:smithy-kotlin-codegen"))
    codegen(libs.smithy.cli)
    codegen(libs.smithy.model)
}

val generateSdk = tasks.named<SmithyBuildTask>("smithyBuild")
generateSdk.configure {
    resolvedCliClasspath.set(codegen)
    runtimeClasspath.set(codegen)
    buildClasspath.set(codegen)
}

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")
kotlin.sourceSets.all {
    optinAnnotations.forEach { languageSettings.optIn(it) }
}

kotlin.sourceSets.getByName("main") {
    kotlin.srcDir(smithy.getPluginProjectionPath("waiter-tests", "kotlin-codegen").map { it.resolve("src/main/kotlin") })
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateSdk)
    kotlinOptions {
        // generated code has warnings unfortunately
        allWarningsAsErrors = false
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
    compileOnly(project(":codegen:smithy-kotlin-codegen"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":runtime:runtime-core"))
    implementation(project(":runtime:smithy-client"))
    implementation(project(":runtime:protocol:http-client"))
    implementation(project(":runtime:observability:telemetry-api"))
    implementation(project(":runtime:observability:telemetry-defaults"))

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test.junit5)
}
