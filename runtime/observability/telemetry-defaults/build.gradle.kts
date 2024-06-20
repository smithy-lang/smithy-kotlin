/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Default telemetry provider"
extra["displayName"] = "Smithy :: Kotlin :: Observability :: Telemetry Defaults"
extra["moduleName"] = "aws.smithy.kotlin.runtime.telemetry"

val coroutinesVersion: String by project
val slf4jVersion: String by project
val atomicFuVersion: String by project
apply(plugin = "kotlinx-atomicfu")

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

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
