/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

extra["displayName"] = "Smithy :: Kotlin :: Compile :: Test"
extra["moduleName"] = "software.amazon.smithy.kotlin.codegen"

val smithyVersion: String by project
val kotestVersion: String by project
val kotlinVersion: String by project
val junitVersion: String by project
val kotlinCompileTestingVersion: String by project

dependencies {
    testImplementation(project(":codegen:smithy-kotlin-codegen"))
    testImplementation(project(":runtime"))
    testImplementation(project(":runtime:runtime-core"))
    testImplementation(project(":runtime:protocol:http"))
    testImplementation(project(":runtime:protocol:http-client-engines:http-client-engine-default"))
    testImplementation(project(":runtime:serde:serde-json"))
    testImplementation(project(":runtime:observability:telemetry-api"))
    testImplementation(project(":runtime:observability:telemetry-defaults"))

    testImplementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    testImplementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:$kotlinCompileTestingVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showStackTraces = true
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
