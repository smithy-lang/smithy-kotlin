/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Lint rules for the AWS SDK for Kotlin"

plugins {
    kotlin("jvm")
}

val ktlintVersion: String by project

kotlin {
    sourceSets {
        val main by getting {
            dependencies {
                implementation("com.pinterest.ktlint:ktlint-core:$ktlintVersion")
            }
        }

        val test by getting {
            dependencies {
                implementation("com.pinterest.ktlint:ktlint-test:$ktlintVersion")
            }
        }
    }
}
