/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
description = "HTTP Core for Smithy services generated by smithy-kotlin"
extra["displayName"] = "Smithy :: Kotlin :: HTTP Core"
extra["moduleName"] = "software.aws.clientrt.http"

// FIXME - atomicfu gradle plugin fails on transformJvmMainAtomicfu task for some reason, for now just use it as a library without the bytecode transform
val atomicFuVersion: String by project
val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":client-runtime:client-rt-core"))
                // exposes: Attributes
                api(project(":client-runtime:utils"))
                // exposes: service+middleware
                api(project(":client-runtime:io"))
                implementation(project(":client-runtime:logging"))

                // HttpClientEngine implements CoroutineScope
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
            }
        }

        commonTest {
            dependencies {
                implementation(project(":client-runtime:testing"))
            }
        }
    }
}