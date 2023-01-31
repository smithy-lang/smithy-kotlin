/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Common tracing classes"
extra["displayName"] = "Smithy :: Kotlin :: Tracing :: Core"
extra["moduleName"] = "aws.smithy.kotlin.runtime.tracing"

val kotlinLoggingVersion: String by project
val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // Necessary for Instant, InternalApi, etc
                api(project(":runtime:runtime-core"))

                // Necessary for Logger
                api(project(":runtime:logging"))

                implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
            }
        }

        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
