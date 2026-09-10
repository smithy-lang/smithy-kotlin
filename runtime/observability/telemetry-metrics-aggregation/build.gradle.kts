/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Backend-agnostic metrics aggregation and export pipeline"
extra["displayName"] = "Smithy :: Kotlin :: Observability :: Metrics Aggregation"
extra["moduleName"] = "aws.smithy.kotlin.runtime.telemetry.metrics.aggregation"

// Multiplatform atomics and locks. Chosen over platform-specific synchronization because the whole
// module lives in `common`: atomicfu's `atomic()` and `reentrantLock()` compile to the efficient
// primitive on each target without expect/actual declarations here. Matches the convention used by
// sibling runtime modules (telemetry-defaults, runtime-core).
apply(plugin = "org.jetbrains.kotlinx.atomicfu")

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // `api`, not `implementation`: this module's public surface exposes telemetry-api
                // types directly. SdkMeterProvider IS-A MeterProvider, and callers must be able to
                // name MeterProvider/Meter/Attributes without adding telemetry-api to their own
                // build. Downgrading this to `implementation` produces "cannot access class" errors
                // at the call site. telemetry-api in turn exposes runtime-core (Instant,
                // Attributes, InternalApi) as `api`.
                api(project(":runtime:observability:telemetry-api"))

                // Needed for the collection loop: CoroutineScope, SupervisorJob, delay, and the
                // suspend-friendly Mutex that serializes collect+export.
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)

                // runTest + virtual time. Essential here: PeriodicMetricReader's tests must advance
                // a 1-minute publish interval without sleeping for a minute.
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
