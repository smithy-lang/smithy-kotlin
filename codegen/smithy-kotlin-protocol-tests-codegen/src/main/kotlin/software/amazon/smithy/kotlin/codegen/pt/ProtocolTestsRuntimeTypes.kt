/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.pt

import software.amazon.smithy.kotlin.codegen.core.GradleConfiguration
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypePackage

val PROTOCOL_TEST_RUNTIME_UTILS = KotlinDependency(
    GradleConfiguration.Implementation,
    "software.amazon.smithy.kotlin.protocolTests.utils",
    "software.amazon.smithy.kotlin",
    "protocol-tests-utils",
    "0.34.17-SNAPSHOT",
)

object ProtocolTestsUtils : RuntimeTypePackage(PROTOCOL_TEST_RUNTIME_UTILS) {
    val TestType = symbol("TestType")
    val Result = symbol("Result")
    val TestResult = symbol("TestResult")
    val writeResults = symbol("writeResults")
}
