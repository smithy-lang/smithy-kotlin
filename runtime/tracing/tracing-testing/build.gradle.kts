/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Common utilities for testing code which requires tracing"
extra["displayName"] = "Smithy :: Kotlin :: Tracing :: Testing"
extra["moduleName"] = "aws.smithy.kotlin.runtime.tracing"

val coroutinesVersion: String by project
val kotlinLoggingVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:tracing:tracing-core"))

                // Necessary for `runTest`
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
