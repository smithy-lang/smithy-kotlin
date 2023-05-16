/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Support for the JSON suite of AWS protocols"
extra["displayName"] = "AWS :: Smithy :: Kotlin :: JSON"
extra["moduleName"] = "aws.smithy.kotlin.runtime.awsprotocol.json"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:smithy-client"))
                api(project(":runtime:protocol:http-client"))
                api(project(":runtime:runtime-core"))
                implementation(project(":runtime:protocol:aws-protocol-core"))
                implementation(project(":runtime:serde"))
                implementation(project(":runtime:serde:serde-json"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:testing"))
                implementation(project(":runtime:protocol:http-test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
