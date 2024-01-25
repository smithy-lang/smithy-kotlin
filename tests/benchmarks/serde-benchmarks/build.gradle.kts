/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.aws.kotlin.repo.tools.smithybuild)
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
                implementation(project(":runtime:serde:serde-json"))
                implementation(project(":runtime:serde:serde-xml"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jvmMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated-src/src"))
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
            warmups = 7
            outputTimeUnit = "ms"
            reportFormat = "text"
        }

        register("json") {
            iterations = 5
            warmups = 7
            outputTimeUnit = "ms"
            reportFormat = "text"
            include(".*json.*")
        }

        register("xml") {
            iterations = 5
            warmups = 7
            outputTimeUnit = "ms"
            reportFormat = "text"
            include(".*xml.*")
        }
    }
}

// Workaround for https://github.com/Kotlin/kotlinx-benchmark/issues/39
afterEvaluate {
    tasks.named<org.gradle.jvm.tasks.Jar>("jvmBenchmarkJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

val codegen by configurations.getting
dependencies {
    codegen(project(":tests:benchmarks:serde-benchmarks-codegen"))
    codegen(libs.smithy.cli)
    codegen(libs.smithy.model)
}

tasks.generateSmithyProjections {
    smithyBuildConfigs.set(files("smithy-build.json"))
}

data class BenchmarkModel(val name: String) {
    val projectionRootDir: File
        get() = layout.buildDirectory.dir("smithyprojections/${project.name}/$name/kotlin-codegen/src/main/kotlin").get().asFile.absoluteFile

    val sourceSetRootDir: File
        get() = layout.buildDirectory.dir("generated-src/src").get().asFile.absoluteFile
}

val benchmarkModels = listOf(
    "twitter",
    "countries-states",
).map { BenchmarkModel(it) }

val stageGeneratedSources = tasks.register("stageGeneratedSources") {
    group = "codegen"
    dependsOn(tasks.generateSmithyProjections)
    doLast {
        benchmarkModels.forEach {
            copy {
                from("${it.projectionRootDir}")
                into("${it.sourceSetRootDir}")
                include("**/model/*.kt")
                include("**/serde/*.kt")
                exclude("**/serde/*OperationSerializer.kt")
                exclude("**/serde/*OperationDeserializer.kt")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(stageGeneratedSources)
}
