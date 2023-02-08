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

description = "HTTP client engine backed by CRT"
extra["displayName"] = "AWS :: SDK :: Kotlin :: HTTP"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http.engine.crt"

val coroutinesVersion: String by project
val atomicFuVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                api(project(":runtime:protocol:http-client"))
                implementation(project(":runtime:logging"))
                implementation(project(":runtime:crt-util"))
                implementation(project(":runtime:tracing:tracing-core"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
            }
        }

        commonTest {
            dependencies {
                implementation(project(":runtime:testing"))
                implementation(project(":runtime:protocol:http-test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
