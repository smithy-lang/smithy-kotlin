/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.codegen.smithyKotlinProjectionSrcDir
import aws.sdk.kotlin.gradle.dsl.skipPublishing

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("aws.sdk.kotlin.gradle.smithybuild")
}

skipPublishing()

val codegen by configurations.getting
dependencies {
    codegen(project(":codegen:smithy-kotlin-codegen"))
    codegen(libs.smithy.cli)
    codegen(libs.smithy.model)
}

tasks.generateSmithyProjections {
    smithyBuildConfigs.set(files("smithy-build.json"))
}

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")
kotlin.sourceSets.all {
    optinAnnotations.forEach { languageSettings.optIn(it) }
}

val projections = listOf(
    "client-mode",
    "client-careful-mode",
)

kotlin.sourceSets.getByName("main") {
    projections.forEach { projectionName ->
        kotlin.srcDir(smithyBuild.smithyKotlinProjectionSrcDir(projectionName))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(tasks.generateSmithyProjections)
    // FIXME - generated code has warnings unfortunately, see https://github.com/awslabs/aws-sdk-kotlin/issues/1169
    kotlinOptions.allWarningsAsErrors = false
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

    testImplementation(libs.kotlin.test.junit5)
}
