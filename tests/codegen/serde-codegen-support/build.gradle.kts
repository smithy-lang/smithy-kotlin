/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
plugins {
    kotlin("jvm")
}

skipPublishing()

description = "Codegen support for serde related integration tests"

dependencies {
    implementation(project(":codegen:smithy-kotlin-codegen"))
}
