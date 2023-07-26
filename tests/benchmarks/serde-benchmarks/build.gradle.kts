/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import software.amazon.smithy.gradle.tasks.SmithyBuild
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.benchmark")
    id("software.amazon.smithy")
}

extra.set("skipPublish", true)

val optinAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.InternalApi")

kotlin {
    sourceSets {
        all {
            optinAnnotations.forEach { languageSettings.optIn(it) }
        }

        val kotlinxBenchmarkVersion: String by project
        val coroutinesVersion: String by project
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:$kotlinxBenchmarkVersion")
                implementation(project(":runtime:serde:serde-json"))
                implementation(project(":runtime:serde:serde-xml"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("${project.buildDir}/generated-src/src")
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

tasks["smithyBuildJar"].enabled = false

val codegen by configurations.creating

dependencies {
    codegen(project(":tests:benchmarks:serde-benchmarks-codegen"))
}

val generateSdk = tasks.create<SmithyBuild>("generateSdk") {
    group = "codegen"
    classpath = configurations.getByName("codegen")
    inputs.file(projectDir.resolve("smithy-build.json"))
    inputs.files(configurations.getByName("codegen"))
}

data class BenchmarkModel(val name: String) {
    val projectionRootDir: File
        get() = project.file("${project.buildDir}/smithyprojections/${project.name}/$name/kotlin-codegen/src/main/kotlin").absoluteFile

    val sourceSetRootDir: File
        get() = project.file("${project.buildDir}/generated-src/src").absoluteFile
}

val benchmarkModels = listOf(
    "twitter",
    "countries-states",
).map { BenchmarkModel(it) }

val stageGeneratedSources = tasks.register("stageGeneratedSources") {
    group = "codegen"
    dependsOn(generateSdk)
    doLast {
        benchmarkModels.forEach {
            copy {
                from("${it.projectionRootDir}")
                into("${it.sourceSetRootDir}")
                include("**/model/*.kt")
                include("**/transform/*.kt")
                exclude("**/transform/*OperationSerializer.kt")
                exclude("**/transform/*OperationDeserializer.kt")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(stageGeneratedSources)
}
