/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
description = "AWS Signer default implementation"
extra["displayName"] = "Smithy :: Kotlin :: Standard AWS Signer"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth.awssigning.default"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:auth:aws-signing-common"))
                implementation(project(":runtime:hashing"))
                implementation(project(":runtime:logging"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:auth:aws-signing-tests"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
