/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
description = "Common test suite for AWS signing"
extra["displayName"] = "Smithy :: Kotlin :: AWS Signing Test Suite"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth.awssigning.tests"

skipPublishing()

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:auth:aws-signing-common"))
                api(project(":runtime:auth:http-auth-aws"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.junit.jupiter.params)
            }
        }

        jvmMain {
            dependencies {
                implementation(project(":runtime:protocol:http"))
                implementation(project(":runtime:protocol:http-test"))
                implementation(libs.ktor.http.cio)
                implementation(libs.ktor.utils)
                implementation(libs.kotlin.test.junit5)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
            // internal test suite, not published
            explicitApi = org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Disabled
        }
    }
}
