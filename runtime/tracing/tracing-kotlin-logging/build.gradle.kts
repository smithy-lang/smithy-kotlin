/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Tracing probe implementation using kotlin-logging framework"
extra["displayName"] = "Smithy :: Kotlin :: Tracing :: Kotlin-logging"
extra["moduleName"] = "aws.smithy.kotlin.runtime.tracing.kotlinlogging"

val kotlinLoggingVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // Necessary for runtime.tracing.* components
                api(project(":runtime:tracing:tracing-core"))

                // Necessary for InternalApi
                api(project(":runtime:utils"))

                implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
