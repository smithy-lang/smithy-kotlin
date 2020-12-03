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
val junitVersion: String by project
val kotlinCompileTestingVersion: String by project

dependencies {
    testImplementation(project(":smithy-kotlin-codegen"))
    testImplementation(project(":client-runtime"))
    testImplementation(project(":client-runtime:client-rt-core"))
    testImplementation(project(":client-runtime:protocol:http"))
    testImplementation(project(":client-runtime:protocol:http-client-engines:http-client-engine-ktor"))

    testImplementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")
    testImplementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:$kotlinCompileTestingVersion")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}