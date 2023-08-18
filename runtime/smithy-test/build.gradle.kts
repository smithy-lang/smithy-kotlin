/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Test utilities for generated Smithy services"
extra["displayName"] = "Smithy :: Kotlin :: Test"
extra["moduleName"] = "aws.smithy.kotlin.runtime.smithy.test"

val coroutinesVersion: String by project
val kotlinVersion: String by project
val kotlinxSerializationVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:protocol:http-client"))
                implementation(project(":runtime:protocol:http-test"))

                implementation(project(":runtime:serde:serde-xml"))

                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test)

                // kotlinx-serialization::JsonElement allows comparing arbitrary JSON docs for equality
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
