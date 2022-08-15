/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Common tracing classes"
extra["displayName"] = "Smithy :: Kotlin :: Tracing :: Core"
extra["moduleName"] = "aws.smithy.kotlin.runtime.tracing"

val kotlinLoggingVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // Necessary for Instant
                api(project(":runtime:runtime-core"))

                // Necessary for Closeable
                api(project(":runtime:io"))

                // Necessary for InternalApi
                api(project(":runtime:utils"))

                // Necessary for Logger
                api(project(":runtime:logging"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
