/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
plugins {
    id("org.jetbrains.kotlinx.benchmark")
}

extra.set("skipPublish", true)

val kotlinxBenchmarkVersion: String by project


kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.3.1")
                implementation(project(":runtime:serde:serde-json"))
                implementation(project(":runtime:testing"))
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
    }
}

// Workaround for https://github.com/Kotlin/kotlinx-benchmark/issues/39
afterEvaluate {
    tasks.named<org.gradle.jvm.tasks.Jar>("jvmBenchmarkJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}