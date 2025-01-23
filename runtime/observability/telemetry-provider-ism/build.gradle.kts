/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Telemetry provider for invocation-scoped metrics"
extra["displayName"] = "Smithy :: Kotlin :: Observability :: Invocation-scoped Metrics Provider"
extra["moduleName"] = "aws.smithy.kotlin.runtime.telemetry.ism"

apply(plugin = "org.jetbrains.kotlinx.atomicfu")

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
                implementation(project(":runtime:protocol:http-client")) // for operation-telemetry attributes
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
