/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
plugins {
    kotlin("jvm")
}

skipPublishing()

description = "Codegen support for serde-benchmarks project"

dependencies {
    implementation(project(":codegen:smithy-kotlin-codegen"))
}
