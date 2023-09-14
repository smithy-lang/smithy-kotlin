/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
plugins {
    kotlin("multiplatform")
    @Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once https://youtrack.jetbrains.com/issue/KTIJ-19369 is fixed
    alias(libs.plugins.kotlinx.benchmark)
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
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-okhttp"))
                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-crt"))

                // mock/embedded server
                implementation(libs.ktor.server.netty)
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
            iterationTime = 5
            iterationTimeUnit = "s"
            warmups = 3
            outputTimeUnit = "s"
            reportFormat = "text"
            advanced("jvmForks", "1")
        }
    }
}

// Workaround for https://github.com/Kotlin/kotlinx-benchmark/issues/39
afterEvaluate {
    tasks.named<org.gradle.jvm.tasks.Jar>("jvmBenchmarkJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
