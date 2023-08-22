/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
plugins {
    kotlin("jvm")
}

extra["displayName"] = "Smithy :: Kotlin :: Compile :: Test"
extra["moduleName"] = "software.amazon.smithy.kotlin.codegen"

skipPublishing()

dependencies {
    testImplementation(project(":codegen:smithy-kotlin-codegen"))
    testImplementation(project(":runtime:runtime-core"))
    testImplementation(project(":runtime:protocol:http"))
    testImplementation(project(":runtime:protocol:http-client-engines:http-client-engine-default"))
    testImplementation(project(":runtime:serde:serde-json"))
    testImplementation(project(":runtime:observability:telemetry-api"))
    testImplementation(project(":runtime:observability:telemetry-defaults"))

    testImplementation(libs.smithy.protocol.test.traits)
    testImplementation(libs.smithy.aws.traits)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core.jvm)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlin.test.junit5)
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
