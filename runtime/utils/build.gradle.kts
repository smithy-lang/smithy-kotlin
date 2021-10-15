/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Utilities for working with the Smithy runtime"
extra["displayName"] = "Smithy :: Kotlin :: Utils"
extra["moduleName"] = "aws.smithy.kotlin.runtime.utils"

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":runtime:testing"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
