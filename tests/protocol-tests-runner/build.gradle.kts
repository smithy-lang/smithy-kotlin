/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow.jar)
    application
}

repositories {
    mavenLocal()
    mavenCentral()
}

skipPublishing()

description = "Protocol test runner"

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.smithy.model)
    implementation(libs.smithy.build)
    implementation(libs.smithy.validation.model)
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.protocol.test.traits)
    implementation(libs.smithy.protocol.traits)
    implementation(project(":tests:protocol-tests-utils"))
    implementation(project(":codegen:smithy-kotlin-protocol-tests-codegen"))
    implementation(project(":codegen:smithy-aws-kotlin-codegen"))
    implementation(project(":codegen:smithy-kotlin-codegen"))
}

application {
    mainClass.set("software.amazon.smithy.kotlin.protocolTests.RunnerKt")
}

tasks {
    shadowJar {
        append("META-INF/smithy/manifest")
        mergeServiceFiles()
    }
}
