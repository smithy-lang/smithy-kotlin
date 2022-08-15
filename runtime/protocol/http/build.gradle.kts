/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "HTTP Core for Smithy services generated by smithy-kotlin"
extra["displayName"] = "Smithy :: Kotlin :: HTTP Core"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http"

// FIXME - atomicfu gradle plugin fails on transformJvmMainAtomicfu task for some reason, for now just use it as a library without the bytecode transform
val atomicFuVersion: String by project
val coroutinesVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                // exposes: Attributes
                api(project(":runtime:utils"))
                implementation(project(":runtime:hashing"))
                // exposes: service+middleware
                api(project(":runtime:io"))
                implementation(project(":runtime:logging"))

                // Necessary for TraceSpan
                api(project(":runtime:tracing:tracing-core"))

                // HttpClientEngine implements CoroutineScope
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
            }
        }

        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation(project(":runtime:tracing:tracing-testing"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}
