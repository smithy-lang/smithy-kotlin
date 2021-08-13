/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Internal test utilities"

kotlin {
    sourceSets {
        metadata {
            dependencies {
                commonMainApi(project(":runtime:utils"))
            }
        }
    }
}
