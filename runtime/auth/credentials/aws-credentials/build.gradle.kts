/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Types for AWS credentials"
extra["displayName"] = "Smithy :: Kotlin :: AWS Credentials"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth.signing.awssigning.common"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // For Instant
                api(project(":runtime:runtime-core"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
