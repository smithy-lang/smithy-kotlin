/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
buildscript {
    val atomicFuVersion: String by project
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicFuVersion")
    }
}

description = "Types for AWS credentials"
extra["displayName"] = "Smithy :: Kotlin :: AWS Credentials"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth.awscredentials"

val coroutinesVersion: String by project
val atomicFuVersion: String by project

apply(plugin = "kotlinx-atomicfu")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // For Instant
                api(project(":runtime:runtime-core"))
                api(project(":runtime:auth:identity-api"))
                implementation(project(":runtime:tracing:tracing-core"))
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
            }
        }
        commonTest {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
