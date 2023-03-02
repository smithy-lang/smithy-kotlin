/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "AWS-related HTTP Auth APIs"
extra["displayName"] = "Smithy :: Kotlin :: AWS HTTP Auth"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http.auth"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                api(project(":runtime:protocol:http"))
                api(project(":runtime:auth:http-auth-api"))
                api(project(":runtime:auth:aws-signing-common"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:protocol:http-client"))
                implementation(project(":runtime:auth:aws-signing-default"))
                implementation(project(":runtime:auth:aws-signing-crt"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
