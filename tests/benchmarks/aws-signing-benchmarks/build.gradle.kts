/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.benchmark")
}

extra.set("skipPublish", true)

val platforms = listOf("common", "jvm")

platforms.forEach { platform ->
    apply(from = rootProject.file("gradle/${platform}.gradle"))
}

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.util.InternalApi")

kotlin {
    sourceSets {
        all {
            val srcDir = if (name.endsWith("Main")) "src" else "test"
            val resourcesPrefix = if (name.endsWith("Test")) "test-" else ""
            // the name is always the platform followed by a suffix of either "Main" or "Test" (e.g. jvmMain, commonTest, etc)
            val platform = name.substring(0, name.length - 4)
            kotlin.srcDir("$platform/$srcDir")
            resources.srcDir("$platform/${resourcesPrefix}resources")
            languageSettings.progressiveMode = true
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
