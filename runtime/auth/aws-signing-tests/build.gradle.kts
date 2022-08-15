/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Common test suite for AWS signing"
extra["displayName"] = "Smithy :: Kotlin :: AWS Signing Test Suite"
extra["moduleName"] = "aws.smithy.kotlin.runtime.auth.awssigning.tests"
extra["skipPublish"] = true

val coroutinesVersion: String by project
val junitVersion: String by project
val kotlinVersion: String by project
val kotlinxSerializationVersion: String by project
val ktorVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:auth:aws-signing-common"))
                implementation(project(":runtime:tracing:tracing-core"))
                implementation("org.jetbrains.kotlin:kotlin-test-common:$kotlinVersion")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common:$kotlinVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
            }
        }

        jvmMain {
            dependencies {
                implementation(project(":runtime:protocol:http"))
                implementation("io.ktor:ktor-http-cio:$ktorVersion")
                implementation("io.ktor:ktor-utils:$ktorVersion")
                implementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
