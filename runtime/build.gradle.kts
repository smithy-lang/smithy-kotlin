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
    id("org.jetbrains.dokka")
    jacoco
}

// configures subprojects with our own KMP conventions and some default dependencies
apply(plugin="aws.sdk.kotlin.kmp")

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

    apply(from = rootProject.file("gradle/publish.gradle"))

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
