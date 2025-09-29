/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Default telemetry provider"
extra["displayName"] = "Smithy :: Kotlin :: Observability :: Telemetry Defaults"
extra["moduleName"] = "aws.smithy.kotlin.runtime.telemetry"

apply(plugin = "org.jetbrains.kotlinx.atomicfu")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // Necessary for Instant, InternalApi, etc
                api(project(":runtime:observability:telemetry-api"))
            }
        }

        jvmMain {
            dependencies {
                implementation(project(":runtime:observability:logging-slf4j2"))
            }
        }

        nativeMain {
            dependencies {
                implementation(project(":runtime:observability:logging-crt"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
