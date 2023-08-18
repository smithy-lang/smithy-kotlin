/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Internal test utilities"

val coroutinesVersion: String by project
val kotlinVersion: String by project
val junitVersion: String by project

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
