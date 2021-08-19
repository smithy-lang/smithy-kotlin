/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Test utilities for generated Smithy services"
extra["displayName"] = "Smithy :: Kotlin :: Test"
extra["moduleName"] = "aws.smithy.kotlin.runtime.smithy.test"

val kotlinVersion: String by project
val kotlinTestVersion: String by project
val kotlinxSerializationVersion: String = "0.20.0"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:protocol:http"))

                implementation(project(":runtime:testing"))
                implementation(project(":runtime:serde:serde-xml"))

                implementation("org.jetbrains.kotlin:kotlin-test-common:$kotlinTestVersion")

                // kotlinx-serialization::JsonElement allows comparing arbitrary JSON docs for equality
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$kotlinxSerializationVersion")
            }
        }
        commonTest {
            dependencies {
                implementation(project(":runtime:testing"))
            }
        }

        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinxSerializationVersion")
                implementation("org.jetbrains.kotlin:kotlin-test:$kotlinTestVersion")
            }
        }
    }
}
