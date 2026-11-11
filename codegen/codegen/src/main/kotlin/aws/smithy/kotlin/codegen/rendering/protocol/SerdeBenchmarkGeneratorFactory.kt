/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.protocol

import aws.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase

/**
 * Factory interface for creating benchmark generators. Implementations are responsible for
 * rendering benchmark classes for protocol test cases tagged with "serde-benchmark".
 */
interface SerdeBenchmarkGeneratorFactory {
    fun renderRequestBenchmark(
        ctx: ProtocolGenerator.GenerationContext,
        writer: KotlinWriter,
        operation: OperationShape,
        className: String,
        testCases: List<HttpRequestTestCase>,
    )

    fun renderResponseBenchmark(
        ctx: ProtocolGenerator.GenerationContext,
        writer: KotlinWriter,
        operation: OperationShape,
        className: String,
        testCases: List<HttpResponseTestCase>,
    )
}
