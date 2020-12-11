/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape

/**
 * Context passed to an individual generator for rendering (writing) a shape
 */
data class RenderingContext(
    val model: Model,
    val symbolProvider: SymbolProvider,
    val writer: KotlinWriter,
    val service: ServiceShape,
    val rootNamespace: String,
    val protocolGenerator: ProtocolGenerator?,
    val integrations: List<KotlinIntegration>
)
