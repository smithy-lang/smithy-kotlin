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

dependencies {
    codegen(project(":codegen:smithy-kotlin-codegen"))
    codegen(libs.smithy.cli)
    codegen(libs.smithy.model)
}

tasks.generateSmithyProjections {
    smithyBuildConfigs.set(files("smithy-build.json"))
}

tasks.kotlinSourcesJar {
    dependsOn(tasks.generateSmithyProjections) // FIXME This is breaking Gradle configuration cache
}

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")
kotlin.sourceSets.all {
    optinAnnotations.forEach { languageSettings.optIn(it) }
}

kotlin.sourceSets.getByName("main") {
    kotlin.srcDir(smithyBuild.smithyKotlinProjectionSrcDir("waiter-tests"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(tasks.generateSmithyProjections) // FIXME This is breaking Gradle configuration cache
    compilerOptions {
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
