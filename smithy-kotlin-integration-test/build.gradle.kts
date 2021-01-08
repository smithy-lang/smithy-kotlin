/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
plugins {
    kotlin("jvm")
}

extra["displayName"] = "Smithy :: Kotlin :: Integration :: Test"
extra["moduleName"] = "software.amazon.smithy.kotlin.integration.test"

val smithyVersion: String by project
val kotestVersion: String by project
val kotlinVersion: String by project
val junitVersion: String by project
val kotlinCompileTestingVersion: String by project

dependencies {
    testImplementation(project(":smithy-kotlin-codegen"))
    testImplementation(project(":client-runtime"))
    testImplementation(project(":client-runtime:client-rt-core"))
    testImplementation(project(":client-runtime:protocol:http"))
    testImplementation(project(":client-runtime:protocol:http-client-engines:http-client-engine-ktor"))
    testImplementation(project(":client-runtime:serde:serde-json"))

    testImplementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    testImplementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:$kotlinCompileTestingVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}