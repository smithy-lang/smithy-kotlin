/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

val sdkVersion: String by project
version = sdkVersion
group = "software.amazon.smithy.kotlin"

plugins {
    alias(libs.plugins.aws.kotlin.repo.tools.kmp)
    alias(libs.plugins.plugin.publish)
    `kotlin-dsl`
}

dependencies {
    implementation(libs.smithy.model)
    implementation(libs.smithy.gradle.base.plugin)

    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        val smithyKotlinBuildPlugin by creating {
            id = "smithy-build"
            implementationClass = "software.amazon.smithy.kotlin.gradle.codegen.SmithyBuildPlugin"
            description = "Wrap for smithy gradle base plugin that provides a DSL for generating smithy-build.json dynamically"
        }
    }
}
