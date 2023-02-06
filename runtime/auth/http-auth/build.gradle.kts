/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "HTTP auth support"
extra["displayName"] = "Smithy :: Kotlin :: HTTP Auth"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth"

val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                api(project(":runtime:protocol:http"))

                // FIXME - for ExecutionContext
                api(project(":runtime:smithy-client"))
            }
        }
    }
}
