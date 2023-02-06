/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Common types for AWS signing"
extra["displayName"] = "Smithy :: Kotlin :: AWS Signing Common"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth.signing.awssigning"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:auth:aws-credentials"))
                api(project(":runtime:protocol:http"))

                // FIXME - this seems off, (Endpoint is consumed by presigner)
                api(project(":runtime:protocol:http-client"))
                implementation(project(":runtime:logging"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
