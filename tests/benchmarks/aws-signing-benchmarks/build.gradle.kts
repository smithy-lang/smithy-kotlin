/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.kmp.*
buildscript {
    dependencies {
        // Add our custom gradle plugin(s) to buildscript classpath (comes from github source)
        classpath("aws.sdk.kotlin:build-plugins") {
            version {
                branch = "kmp-plugin"
            }
        }
    }
}

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.benchmark")
}

extra.set("skipPublish", true)


val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")

kotlin {
    // FIXME - refactor how we expose this
    configureCommon()
    configureJvm()
    configureSourceSetsConvention()

    sourceSets {
        all {
            optinAnnotations.forEach { languageSettings.optIn(it) }
        }

        val kotlinxBenchmarkVersion: String by project
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:$kotlinxBenchmarkVersion")
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
