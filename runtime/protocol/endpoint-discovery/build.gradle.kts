/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Support for automatic endpoint discovery"
extra["displayName"] = "AWS :: Smithy :: Kotlin :: EndpointDiscovery"
extra["moduleName"] = "aws.smithy.kotlin.runtime.endpoints.discovery"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                implementation(project(":runtime:auth:aws-credentials"))
                implementation(project(":runtime:protocol:http-client"))
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
