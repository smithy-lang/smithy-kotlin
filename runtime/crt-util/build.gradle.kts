/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

apply(plugin = "kotlinx-atomicfu")

description = "Utilities for working with AWS CRT Kotlin"
extra["displayName"] = "Smithy :: Kotlin :: CRT :: Util"
extra["moduleName"] = "aws.smithy.kotlin.runtime.crt"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                api(libs.crt.kotlin)
                api(project(":runtime:protocol:http"))
                implementation(libs.kotlinx.atomicfu)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
