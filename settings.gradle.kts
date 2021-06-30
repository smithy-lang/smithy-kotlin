/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

pluginManagement {
    repositories {
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
        google()
        gradlePluginPortal()
    }

    // configure the smithy-gradle plugin version
    plugins {
        id("org.jetbrains.dokka") version "1.4.32"
    }
}

rootProject.name = "smithy-kotlin"

include(":smithy-kotlin-codegen")

include(":runtime")
include(":runtime:runtime-core")
include(":runtime:logging")
include(":runtime:testing")
include(":runtime:smithy-test")
include(":runtime:utils")
include(":runtime:io")
include(":runtime:serde")
include(":runtime:serde:serde-json")
include(":runtime:serde:serde-xml")
include(":runtime:serde:serde-form-url")
include(":runtime:protocol:http")
include(":runtime:protocol:http-client-engines:http-client-engine-ktor")

include(":compile-tests")


// FIXME - intellij DOES NOT like this project included. Everything builds fine but intellij 2020.x
// will not resolve symbols from dependencies in any other subproject once included. 2019.x seems to work fine
// to run with from CLI use `./gradlew -DandroidEmulatorTests=true :android-test:connectedAndroidTest`
if (System.getProperties().containsKey("androidEmulatorTests")) {
    // android integration test project to verify runtime support on Android devices
    // at target API levels
    include(":android-test")
}

include(":ktlint-rules")
