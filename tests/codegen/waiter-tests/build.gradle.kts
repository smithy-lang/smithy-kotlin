/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import software.amazon.smithy.gradle.tasks.SmithyBuild
import software.amazon.smithy.gradle.tasks.Validate as SmithyValidate

plugins {
    kotlin("jvm")
    id("software.amazon.smithy")
}

extra.set("skipPublish", true)

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")
kotlin.sourceSets.all {
    optinAnnotations.forEach { languageSettings.optIn(it) }
}

kotlin.sourceSets.getByName("main") {
    kotlin.srcDir("${project.buildDir}/smithyprojections/waiter-tests/waiter-tests/kotlin-codegen/src/main/kotlin")
}

tasks["smithyBuildJar"].enabled = false

val smithyVersion: String by project
val codegen by configurations.creating
dependencies {
    codegen(project(":codegen:smithy-kotlin-codegen"))
    codegen("software.amazon.smithy:smithy-cli:$smithyVersion")
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>{
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
    val kotlinVersion: String by project
    val coroutinesVersion: String by project

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    compileOnly(project(":codegen:smithy-kotlin-codegen"))
    implementation(project(":runtime:runtime-core"))
    implementation(project(":runtime:smithy-client"))
    implementation(project(":runtime:protocol:http-client"))
    api(project(":runtime:tracing:tracing-core"))

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
}

