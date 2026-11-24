/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Internal test utilities"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.test)
                api(libs.kotlinx.io.core)
                implementation(project(":runtime:runtime-core")) // for Uuid
            }
        }
        jvmMain {
            dependencies {
                api(libs.junit.jupiter)
                implementation(libs.kotlin.test.junit5)
                api(libs.kotlinx.coroutines.test)
            }
        }
        nativeMain {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
