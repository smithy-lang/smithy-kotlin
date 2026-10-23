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

// The `kotlin-dsl` plugin passes `-Xuse-fir-lt=false`, which Kotlin 2.4.20+ warns is deprecated
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = false
    }
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
