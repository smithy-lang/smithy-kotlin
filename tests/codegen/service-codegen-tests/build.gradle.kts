/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing

plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.aws.kotlin.repo.tools.smithybuild)
}

skipPublishing()

val optinAnnotations = listOf("kotlin.RequiresOptIn")
kotlin.sourceSets.all {
    optinAnnotations.forEach { languageSettings.optIn(it) }
}

// Create a task to run the DefaultServiceGeneratorTestKt file
val runServiceGenerator by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Run the DefaultServiceGeneratorTestKt file"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.test.DefaultServiceGeneratorTestKt")
}

tasks.test {
    dependsOn(runServiceGenerator)
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=aws.smithy.kotlin.runtime.InternalApi",
            "-opt-in=kotlin.io.path.ExperimentalPathApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {

    compileOnly(project(":codegen:smithy-kotlin-codegen"))

    implementation(project(":codegen:smithy-kotlin-codegen"))
    implementation(project(":codegen:smithy-aws-kotlin-codegen"))
    implementation(project(":codegen:smithy-kotlin-codegen-testutils"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.cbor)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core.jvm)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(project(":codegen:smithy-kotlin-codegen-testutils"))
    testImplementation(project(":codegen:smithy-kotlin-codegen"))
    testImplementation(project(":codegen:smithy-aws-kotlin-codegen"))
    testApi(project(":runtime:auth:aws-signing-common"))
    testApi(project(":runtime:auth:http-auth-aws"))
    testApi(project(":runtime:auth:aws-signing-default"))

    testImplementation(gradleTestKit())

    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.serialization.cbor)
}
