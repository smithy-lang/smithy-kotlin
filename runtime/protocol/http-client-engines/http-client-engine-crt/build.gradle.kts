/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "HTTP client engine backed by CRT"
extra["displayName"] = "AWS :: SDK :: Kotlin :: HTTP"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http.engine.crt"

apply(plugin = "org.jetbrains.kotlinx.atomicfu")

kotlin {
    sourceSets {
        jvmAndNativeMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                api(project(":runtime:protocol:http-client"))
                implementation(project(":runtime:crt-util"))
                implementation(project(":runtime:observability:telemetry-api"))
                implementation(libs.kotlinx.coroutines.core)
                api(libs.crt.kotlin)
            }
        }

        jvmAndNativeTest {
            dependencies {
                implementation(project(":runtime:testing"))
                implementation(project(":runtime:protocol:http-test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
