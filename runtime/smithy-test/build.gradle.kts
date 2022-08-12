/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Test utilities for generated Smithy services"
extra["displayName"] = "Smithy :: Kotlin :: Test"
extra["moduleName"] = "aws.smithy.kotlin.runtime.smithy.test"

val coroutinesVersion: String by project
val kotlinVersion: String by project
val kotlinxSerializationVersion: String = "0.20.0"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:protocol:http"))

                implementation(project(":runtime:serde:serde-xml"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation("org.jetbrains.kotlin:kotlin-test-common:$kotlinVersion")

                // kotlinx-serialization::JsonElement allows comparing arbitrary JSON docs for equality
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$kotlinxSerializationVersion")
            }
        }

        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinxSerializationVersion")
                implementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
