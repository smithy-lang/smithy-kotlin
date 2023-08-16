/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.configurePublishing
import aws.sdk.kotlin.gradle.kmp.*
plugins {
    id("org.jetbrains.dokka")
    jacoco
}

val sdkVersion: String by project

val coroutinesVersion: String by project
val kotestVersion: String by project

subprojects {
    if (!needsKmpConfigured) return@subprojects
    group = "aws.smithy.kotlin"
    version = sdkVersion

    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
        plugin("org.jetbrains.dokka")
    }

    configurePublishing("smithy-kotlin")

    kotlin {
        explicitApi()

        sourceSets {
            // dependencies available for all subprojects
            named("commonMain") {
                dependencies {
                    // refactor to only add this to projects that need it
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                }
            }

            named("commonTest") {
                dependencies {
                    implementation("io.kotest:kotest-assertions-core:$kotestVersion")
                }
            }

            named("jvmTest") {
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$coroutinesVersion")
                    implementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
                }
            }
        }
    }

    kotlin.sourceSets.all {
        // Allow subprojects to use internal APIs
        // See https://kotlinlang.org/docs/reference/opt-in-requirements.html#opting-in-to-using-api
        listOf("kotlin.RequiresOptIn").forEach { languageSettings.optIn(it) }
    }

    dependencies {
        dokkaPlugin(project(":dokka-smithy"))
    }
}
