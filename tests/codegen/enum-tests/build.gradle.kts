/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.codegen.smithyKotlinProjectionSrcDir
import aws.sdk.kotlin.gradle.publishing.skipPublishing

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.aws.kotlin.repo.tools.smithybuild)
}

skipPublishing()

val codegen by configurations.getting
dependencies {
    codegen(project(":codegen:codegen"))
    codegen(libs.smithy.cli)
    codegen(libs.smithy.model)
}

tasks.generateSmithyProjections {
    smithyBuildConfigs.set(files("smithy-build.json"))
}

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = true
            }
        }
    }

    sourceSets {
        all {
            optinAnnotations.forEach { languageSettings.optIn(it) }
        }

        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(project(":runtime:runtime-core"))
                implementation(project(":runtime:smithy-client"))
                implementation(project(":runtime:protocol:http-client"))
                implementation(project(":runtime:observability:telemetry-api"))
                implementation(project(":runtime:observability:telemetry-defaults"))
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            kotlin.srcDir(smithyBuild.smithyKotlinProjectionSrcDir("enum-tests"))
            dependencies {
                compileOnly(project(":codegen:codegen"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(tasks.generateSmithyProjections)
}
