/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Internal test utilities"

val coroutinesVersion: String by project
val kotlinVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-common:$kotlinVersion")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common:$kotlinVersion")
            }
        }
        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                api(project(":runtime:utils"))
            }
        }
    }
}
