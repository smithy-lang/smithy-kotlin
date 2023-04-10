/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "HTTP auth interfaces"
extra["displayName"] = "Smithy :: Kotlin :: HTTP Auth API"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http.auth"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:auth:identity-api"))
                api(project(":runtime:protocol:http"))
            }
        }
    }
}
