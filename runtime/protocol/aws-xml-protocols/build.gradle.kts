/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Support for the XML suite of AWS protocols"
extra["displayName"] = "AWS :: Smithy :: Kotlin :: XML"
extra["moduleName"] = "aws.smithy.kotlin.runtime.awsprotocol.xml"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:protocol:http"))
                api(project(":runtime:runtime-core"))
                implementation(project(":runtime:protocol:aws-protocol-core"))
                implementation(project(":runtime:serde"))
                implementation(project(":runtime:serde:serde-xml"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:testing"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
