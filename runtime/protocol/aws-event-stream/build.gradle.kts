/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Support for the vnd.amazon.event-stream content type"
extra["displayName"] = "AWS :: Smithy :: Kotlin :: Protocols :: Event Stream"
extra["moduleName"] = "aws.smithy.kotlin.runtime.awsprotocol.eventstream"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                implementation(project(":runtime:observability:telemetry-api"))

                // exposes AwsSigningConfig
                api(project(":runtime:auth:aws-signing-common"))

                api(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:testing"))
                api(libs.kotlinx.coroutines.test)
                implementation(project(":runtime:auth:aws-signing-default"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
