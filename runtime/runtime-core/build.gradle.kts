/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinx.serialization)
}

description = "Core runtime for Smithy clients and services generated by smithy-kotlin"
extra["displayName"] = "Smithy :: Kotlin :: Runtime"
extra["moduleName"] = "aws.smithy.kotlin.runtime"

apply(plugin = "org.jetbrains.kotlinx.atomicfu")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.okio)
                // Coroutines' locking features are used in retry token bucket implementations
                api(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }

        nativeMain {
            dependencies {
                api(libs.crt.kotlin)
            }
        }

        commonTest {
            dependencies {
                // Coroutines' locking features are used in retry token bucket implementations
                api(libs.kotlinx.coroutines.test)
                implementation(project(":runtime:testing"))
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kaml)
            }
        }

        nativeMain {
            dependencies {
                implementation(libs.kotlin.multiplatform.bignum)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }

    targets.withType<KotlinNativeTarget> {
        compilations["main"].cinterops {
            val interopDir = "$projectDir/native/src/nativeInterop/cinterop"
            create("environ") {
                includeDirs(interopDir)
                packageName("aws.smithy.platform.posix")
                headers(listOf("$interopDir/environ.h"))
            }
        }
    }
}
