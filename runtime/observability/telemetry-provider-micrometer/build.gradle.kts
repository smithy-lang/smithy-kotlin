/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Telemetry provider based on MicroMeter"
extra["displayName"] = "Smithy :: Kotlin :: Observability :: MicroMeter Provider"
extra["moduleName"] = "aws.smithy.kotlin.runtime.telemetry.micrometer"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:observability:telemetry-api"))
                implementation(project(":runtime:observability:telemetry-defaults"))
            }
        }

        jvmMain {
            dependencies {
                api(libs.micrometer.core)
            }
        }
        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
