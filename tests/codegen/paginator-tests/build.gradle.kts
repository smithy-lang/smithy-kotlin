/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
import software.amazon.smithy.gradle.tasks.SmithyBuild

plugins {
    kotlin("multiplatform")
    id("software.amazon.smithy")
}

buildscript {
    val smithyVersion: String by project
    dependencies {
        classpath("software.amazon.smithy:smithy-cli:$smithyVersion")
    }
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
            val resourcesPrefix = if (name.endsWith("Test")) "test-" else  ""
            // the name is always the platform followed by a suffix of either "Main" or "Test" (e.g. jvmMain, commonTest, etc)
            val platform = name.substring(0, name.length - 4)
            kotlin.srcDir("$platform/$srcDir")
            resources.srcDir("$platform/${resourcesPrefix}resources")
            languageSettings.progressiveMode = true
            optinAnnotations.forEach { languageSettings.optIn(it) }
        }

        val coroutinesVersion: String by project
        val sdkVersion: String by project
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("aws.smithy.kotlin:runtime-core:$sdkVersion")
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("${project.buildDir}/generated-src/src")
        }
    }
}

tasks["smithyBuildJar"].enabled = false

val codegen by configurations.creating

val generateSdk = tasks.create<SmithyBuild>("generateSdk") {
    group = "codegen"
    classpath = configurations.getByName("codegen")
    println(configurations.getByName("codegen"))
    inputs.file(projectDir.resolve("smithy-build.json"))
    inputs.files(configurations.getByName("codegen"))
}

data class CodegenSourceInfo(val name: String) {
    val projectionRootDir: File
        get() = project.file("${project.buildDir}/smithyprojections/${project.name}/$name/kotlin-codegen/src/main/kotlin").absoluteFile

    val sourceSetRootDir: File
        get() = project.file("${project.buildDir}/generated-src/src").absoluteFile
}

val codegenSourceInfo = listOf("paginator-tests").map{ CodegenSourceInfo(it) }


val stageGeneratedSources = tasks.register("stageGeneratedSources") {
    group = "codegen"
    dependsOn(generateSdk)
    doLast {
        codegenSourceInfo.forEach {
            copy {
                from("${it.projectionRootDir}")
                into("${it.sourceSetRootDir}")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>{
    dependsOn(stageGeneratedSources)
}

dependencies {
    implementation(project(":smithy-kotlin-codegen"))
}