/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

description = "CBOR serialization and deserialization for Smithy services generated by smithy-kotlin"
extra["displayName"] = "Smithy :: Kotlin :: Serde :: CBOR"
extra["moduleName"] = "aws.smithy.kotlin.runtime.serde.cbor"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":runtime:serde"))
                api(project(":runtime:protocol:http"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}
