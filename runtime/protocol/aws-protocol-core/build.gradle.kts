/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Common AWS protocol support"
extra["displayName"] = "AWS :: Smithy :: Kotlin :: AWS Protocols"
extra["moduleName"] = "aws.smithy.kotlin.runtime.awsprotocol"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:protocol:http"))
                api(project(":runtime:runtime-core"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
