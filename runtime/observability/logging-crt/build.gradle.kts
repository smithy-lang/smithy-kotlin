/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Logging provider based on CRT"
extra["displayName"] = "Smithy :: Kotlin :: Observability :: CRT"
extra["moduleName"] = "aws.smithy.kotlin.runtime.telemetry.logging.crt"

kotlin {
    sourceSets {
        nativeMain {
            dependencies {
                api(project(":runtime:observability:telemetry-api"))
                api(libs.crt.kotlin)
            }
        }
    }
}
