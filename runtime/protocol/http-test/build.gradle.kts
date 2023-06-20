/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Utilities for testing HTTP requests"
extra["displayName"] = "Smithy :: Kotlin :: HTTP Test"
extra["moduleName"] = "aws.smithy.kotlin.runtime.httptest"

val coroutinesVersion: String by project
val kotlinVersion: String by project
val ktorVersion: String by project
val kotlinxSerializationVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:protocol:http-client"))

                implementation("org.jetbrains.kotlin:kotlin-test-common:$kotlinVersion")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common:$kotlinVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
            }
        }
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }
        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
                api("io.ktor:ktor-server-cio:$ktorVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
