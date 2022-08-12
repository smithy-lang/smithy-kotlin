/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

buildscript {
    val atomicFuVersion: String by project
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicFuVersion")
    }
}

apply(plugin = "kotlinx-atomicfu")

description = "Utilities for working with AWS CRT Kotlin"
extra["displayName"] = "Smithy :: Kotlin :: CRT :: Util"
extra["moduleName"] = "aws.smithy.kotlin.runtime.crt"

val atomicFuVersion: String by project
val coroutinesVersion: String by project
val crtKotlinVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                api("aws.sdk.kotlin.crt:aws-crt-kotlin:$crtKotlinVersion")
                api(project(":runtime:protocol:http"))
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
            }
        }
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
