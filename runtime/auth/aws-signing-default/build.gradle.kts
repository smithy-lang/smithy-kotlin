/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "AWS Signer default implementation"
extra["displayName"] = "Smithy :: Kotlin :: Standard AWS Signer"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth.awssigning"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:auth:aws-signing-common"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:auth:aws-signing-tests"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
