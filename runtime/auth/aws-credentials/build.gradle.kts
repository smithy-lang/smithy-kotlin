/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Types for AWS credentials"
extra["displayName"] = "Smithy :: Kotlin :: AWS Credentials"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth.awscredentials"

apply(plugin = "kotlinx-atomicfu")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // For Instant
                api(project(":runtime:runtime-core"))
                api(project(":runtime:auth:identity-api"))
                implementation(project(":runtime:observability:telemetry-api"))
                implementation(libs.kotlinx.atomicfu)
            }
        }
        commonTest {
            dependencies {
                api(libs.kotlinx.coroutines.test)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
