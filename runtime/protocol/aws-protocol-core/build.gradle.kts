/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Common AWS protocol support"
extra["displayName"] = "AWS :: Smithy :: Kotlin :: AWS Protocols"
extra["moduleName"] = "aws.smithy.kotlin.runtime.awsprotocol"

val coroutinesVersion: String by project

apply(plugin = "kotlinx-atomicfu")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:protocol:http"))
                api(project(":runtime:runtime-core"))
                api(project(":runtime:protocol:http-client"))
                api(project(":runtime:smithy-client"))
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":runtime:protocol:http-test"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
