/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Utilities for testing HTTP requests"
extra["displayName"] = "Smithy :: Kotlin :: HTTP Test"
extra["moduleName"] = "aws.smithy.kotlin.runtime.httptest"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:protocol:http-client"))

                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.kotlin.test.junit5)
                api(libs.ktor.server.cio)
            }
        }

        jvmAndNativeMain {
            dependencies {
                api(libs.ktor.server.cio)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.server.core)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
