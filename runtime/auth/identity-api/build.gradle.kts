/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Identity APIs"
extra["displayName"] = "Smithy :: Kotlin :: Identity"
extra["moduleName"] = "aws.smithy.kotlin.runtime.identity"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
            }
        }
    }
}
