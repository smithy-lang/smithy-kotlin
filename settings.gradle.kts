/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

pluginManagement {
    repositories {
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
        google()
        gradlePluginPortal()
    }

    // configure default plugin versions
    plugins {
        val kotlinVersion: String by settings
        val dokkaVersion: String by settings
        val kotlinxBenchmarkVersion: String by settings
        val smithyGradleVersion: String by settings
        id("org.jetbrains.dokka") version dokkaVersion
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.multiplatform") version kotlinVersion
        id("org.jetbrains.kotlinx.benchmark") version kotlinxBenchmarkVersion
        id("software.amazon.smithy") version smithyGradleVersion
    }
}

rootProject.name = "smithy-kotlin"

include(":dokka-smithy")

include(":ktlint-rules")

include(":runtime")
include(":runtime:auth:aws-credentials")
include(":runtime:auth:aws-signing-common")
include(":runtime:auth:aws-signing-crt")
include(":runtime:auth:aws-signing-default")
include(":runtime:auth:aws-signing-tests")
include(":runtime:crt-util")
include(":runtime:hashing")
include(":runtime:io")
include(":runtime:logging")
include(":runtime:protocol:http")
include(":runtime:protocol:http-client-engines:http-client-engine-crt")
include(":runtime:protocol:http-client-engines:http-client-engine-default")
include(":runtime:protocol:http-client-engines:http-client-engine-ktor")
include(":runtime:protocol:http-client-engines:test-suite")
include(":runtime:protocol:http-test")
include(":runtime:runtime-core")
include(":runtime:serde")
include(":runtime:serde:serde-form-url")
include(":runtime:serde:serde-json")
include(":runtime:serde:serde-xml")
include(":runtime:smithy-test")
include(":runtime:testing")
include(":runtime:utils")

include(":smithy-kotlin-codegen")

include(":tests")
include(":tests:benchmarks:aws-signing-benchmarks")
include(":tests:benchmarks:serde-benchmarks-codegen")
include(":tests:benchmarks:serde-benchmarks")
include(":tests:compile")
include(":tests:codegen:paginator-tests")
include(":tests:codegen:waiter-tests")
