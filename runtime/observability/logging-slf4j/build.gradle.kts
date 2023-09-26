/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
description = "Logging provider based on SLF4J 1.x"
extra["displayName"] = "Smithy :: Kotlin :: Observability :: SLF4J binding"
extra["moduleName"] = "aws.smithy.kotlin.runtime.telemetry"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:observability:telemetry-api"))
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.slf4j.api.v1x)
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}

// declare explicit additional capabilities that conflict with `logger-slf4j2` such that consumers
// are forced to choose
// listOf("jvmApiElements", "jvmRuntimeElements")
//     .forEach {
//         configurations.getByName(it) {
//             outgoing {
//                 // NOTE: as soon as you declare explicit capabilities you have to include the implicit ones,
//                 // specifically the one defined by the GAV coordinates
//                 capability("$group:$name:$version")
//                 capability("aws.smithy.kotlin:slf4j-logger-provider:$version")
//             }
//         }
//     }
