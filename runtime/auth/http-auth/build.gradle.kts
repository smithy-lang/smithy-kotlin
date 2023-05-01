/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "HTTP auth support"
extra["displayName"] = "Smithy :: Kotlin :: HTTP Auth"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http.auth"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                api(project(":runtime:protocol:http"))
                api(project(":runtime:auth:http-auth-api"))
            }
        }

        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
