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
description = "HTTP client abstractions"
extra["displayName"] = "Smithy :: Kotlin :: HTTP Client"
extra["moduleName"] = "aws.smithy.kotlin.runtime.http"

val coroutinesVersion: String by project
val atomicFuVersion: String by project
apply(plugin = "kotlinx-atomicfu")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:runtime-core"))
                api(project(":runtime:smithy-client"))
                api(project(":runtime:protocol:http"))

                implementation(project(":runtime:logging"))
                // exposes: TracingContext.TraceSpan
                api(project(":runtime:tracing:tracing-core"))

                // HttpClientEngine implements CoroutineScope
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
            }
        }

        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
