/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "AWS Signer backed by CRT"
extra["displayName"] = "Smithy :: Kotlin :: CRT AWS Signer"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth.awssigning.crt"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:auth:aws-signing-common"))
                implementation(project(":runtime:crt-util"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:auth:aws-signing-tests"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
