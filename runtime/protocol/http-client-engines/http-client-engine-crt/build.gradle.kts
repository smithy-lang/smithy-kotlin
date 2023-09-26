/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
apply(plugin = "kotlinx-atomicfu")

description = "HTTP client engine backed by CRT"
extra["displayName"] = "AWS :: SDK :: Kotlin :: HTTP"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http.engine.crt"

kotlin {
    sourceSets {
        getByName("jvmAndNativeMain") {
            dependencies {
                api(project(":runtime:runtime-core"))
                api(project(":runtime:protocol:http-client"))
                implementation(project(":runtime:crt-util"))
                implementation(project(":runtime:observability:telemetry-api"))

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.atomicfu)
            }
        }

        getByName("jvmAndNativeTest") {
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
