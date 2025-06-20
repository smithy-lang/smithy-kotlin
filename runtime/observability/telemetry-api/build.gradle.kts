/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "API for telemetry data"
extra["displayName"] = "Smithy :: Kotlin :: Observability :: Telemetry API"
extra["moduleName"] = "aws.smithy.kotlin.runtime.telemetry"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // Necessary for Instant, InternalApi, etc
                api(project(":runtime:runtime-core"))
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
