/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    kotlin("jvm")
}

description = "Codegen support for serde-benchmarks project"

dependencies {
    implementation(project(":smithy-kotlin-codegen"))
}