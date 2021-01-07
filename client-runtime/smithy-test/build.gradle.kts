/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

description = "Test utilities for generated Smithy services"
extra["displayName"] = "Smithy :: Kotlin :: Test"
extra["moduleName"] = "software.aws.clientrt.smithy.test"

val kotlinVersion: String by project
val kotlinxSerializationVersion: String = "1.0.1"

repositories {
    jcenter()
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":client-runtime:protocol:http"))

                // FIXME - we likely want to replicate the runBlocking and not depend on this or else we would
                // have to publish this which was intended to be internal
                implementation(project(":client-runtime:testing"))

                implementation("org.jetbrains.kotlin:kotlin-test-common:$kotlinVersion")

                // kotlinx-serialization::JsonElement allows comparing arbitrary JSON docs for equality
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
            }
        }

        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
            }
        }

        jsMain {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-js:$kotlinVersion")
            }
        }
    }
}
