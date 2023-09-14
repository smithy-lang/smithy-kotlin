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
            }
        }
        jvmMain {
            dependencies {
                api(libs.junit.jupiter)
                implementation(libs.kotlin.test)
                api(libs.kotlinx.coroutines.test)
            }
        }
    }
}
