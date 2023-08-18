/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    kotlin("plugin.serialization") version "1.8.21"
}

description = "Core runtime for Smithy clients and services generated by smithy-kotlin"
extra["displayName"] = "Smithy :: Kotlin :: Runtime"
extra["moduleName"] = "aws.smithy.kotlin.runtime"

apply(plugin = "kotlinx-atomicfu")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.okio)
                implementation(libs.kotlinx.atomicfu)
                // Coroutines' locking features are used in retry token bucket implementations
                api(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                // Coroutines' locking features are used in retry token bucket implementations
                api(libs.kotlinx.coroutines.test)
                implementation(libs.kaml)
                implementation(project(":runtime:testing"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
