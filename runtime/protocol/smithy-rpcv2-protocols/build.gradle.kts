/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Support for the RPC v2 suite of Smithy protocols"
extra["displayName"] = "AWS :: Smithy :: Kotlin :: JSON"
extra["moduleName"] = "aws.smithy.kotlin.runtime.awsprotocol.rpcv2"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:smithy-client"))
                api(project(":runtime:protocol:http-client"))
                api(project(":runtime:runtime-core"))
                implementation(project(":runtime:protocol:aws-protocol-core"))
                implementation(project(":runtime:serde"))
                implementation(project(":runtime:serde:serde-cbor"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:testing"))
                implementation(project(":runtime:protocol:http-test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}

dokka {
    modulePath = "smithy-rpcv2-protocols"
}
