/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Utilities for working with the Smithy runtime"
extra["displayName"] = "Smithy :: Kotlin :: Utils"
extra["moduleName"] = "aws.smithy.kotlin.runtime.utils"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":runtime:testing"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
