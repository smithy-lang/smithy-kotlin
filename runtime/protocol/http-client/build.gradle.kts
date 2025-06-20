/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "HTTP client abstractions"
extra["displayName"] = "Smithy :: Kotlin :: HTTP Client"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http"

apply(plugin = "org.jetbrains.kotlinx.atomicfu")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                api(project(":runtime:smithy-client"))
                api(project(":runtime:protocol:http"))
                api(project(":runtime:auth:http-auth"))

                implementation(project(":runtime:observability:telemetry-api"))

                // HttpClientEngine implements CoroutineScope
                api(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":runtime:protocol:http-test"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
