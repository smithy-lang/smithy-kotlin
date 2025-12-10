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
    website = "https://github.com/smithy-lang/smithy-kotlin"
    vcsUrl = "https://github.com/smithy-lang/smithy-kotlin.git"

    plugins {
        val smithyKotlinBuildPlugin by creating {
            id = "software.amazon.smithy.kotlin.smithy-build"
            displayName = "Smithy Kotlin Build Plugin"
            implementationClass = "software.amazon.smithy.kotlin.SmithyBuildPlugin"
            description = "Wrapper for Smithy Gradle base plugin that provides a DSL for generating smithy-build.json dynamically"
            tags = listOf("smithy", "kotlin", "build")
        }
    }
}
