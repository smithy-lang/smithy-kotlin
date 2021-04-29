/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Custom rules for ktlint"

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

    sourceSets.all {
        kotlin.srcDir("src/$name/kotlin")
        resources.srcDir("src/$name/resources")
    }
}
