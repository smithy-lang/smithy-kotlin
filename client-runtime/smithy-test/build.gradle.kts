/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Test utilities for generated Smithy services"
extra["displayName"] = "Smithy :: Kotlin :: Test"
extra["moduleName"] = "software.aws.clientrt.smithy.test"

val kotlinVersion: String by project
val kotlinxSerializationVersion: String = "0.20.0"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":client-runtime:protocol:http"))

                implementation(project(":client-runtime:testing"))
                implementation(project(":client-runtime:serde:serde-xml"))

                implementation("org.jetbrains.kotlin:kotlin-test-common:$kotlinVersion")

                // kotlinx-serialization::JsonElement allows comparing arbitrary JSON docs for equality
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$kotlinxSerializationVersion")
            }
        }
        commonTest {
            dependencies {
                implementation(project(":client-runtime:testing"))
            }
        }

        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinxSerializationVersion")
                implementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
            }
        }
    }
}
