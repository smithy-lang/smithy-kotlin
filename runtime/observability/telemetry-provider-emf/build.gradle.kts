/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Telemetry provider for CloudWatch Embedded Metric Format (EMF)"
extra["displayName"] = "Smithy :: Kotlin :: Observability :: EMF Provider"
extra["moduleName"] = "aws.smithy.kotlin.runtime.telemetry.emf"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:observability:telemetry-api"))
                implementation(project(":runtime:observability:telemetry-defaults"))
                implementation(project(":runtime:serde:serde-json"))
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
