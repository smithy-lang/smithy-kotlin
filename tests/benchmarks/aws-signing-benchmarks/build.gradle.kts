/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.aws.kotlin.repo.tools.kmp)
}

skipPublishing()

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")

kotlin {
    sourceSets {
        all {
            optinAnnotations.forEach { languageSettings.optIn(it) }
        }

        commonMain {
            dependencies {
                implementation(libs.kotlinx.benchmark.runtime)
                implementation(project(":runtime:auth:aws-signing-crt"))
                implementation(project(":runtime:auth:aws-signing-default"))
                implementation(project(":runtime:auth:aws-signing-tests"))
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
    }

    configurations {
        getByName("main") {
            iterations = 5
            iterationTime = 3
            iterationTimeUnit = "s"
            warmups = 7
            outputTimeUnit = "us"
            reportFormat = "text"
        }
    }
}

// Workaround for https://github.com/Kotlin/kotlinx-benchmark/issues/39
afterEvaluate {
    tasks.named<org.gradle.jvm.tasks.Jar>("jvmBenchmarkJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
