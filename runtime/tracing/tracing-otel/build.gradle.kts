/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "Tracing shim layer for OpenTelemetry"
extra["displayName"] = "Smithy :: Kotlin :: Tracing :: OTeL"
extra["moduleName"] = "aws.smithy.kotlin.runtime.tracing.otel"

val coroutinesVersion: String by project
val otelVersion: String by project

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":runtime:tracing:tracing-core"))
                api("io.opentelemetry:opentelemetry-api:$otelVersion")
                // TODO - not stable yet?
                api("io.opentelemetry:opentelemetry-api-logs:$otelVersion-alpha")
            }
        }

        jvmTest {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
