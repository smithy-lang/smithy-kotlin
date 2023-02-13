/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Support for the vnd.amazon.event-stream content type"
extra["displayName"] = "AWS :: Smithy :: Kotlin :: Protocols :: Event Stream"
extra["moduleName"] = "aws.smithy.kotlin.runtime.awsprotocol.eventstream"

val coroutinesVersion: String by project
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                implementation(project(":runtime:tracing:tracing-core"))

                // exposes AwsSigningConfig
                api(project(":runtime:auth:aws-signing-common"))

                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:testing"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation(project(":runtime:auth:aws-signing-default"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
