/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.model.shapes.OperationShape

/**
 * Default endpoint resolver middleware
 */
class ResolveEndpointMiddleware : ProtocolMiddleware {
    override val name: String = "ResolveEndpoint"

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.addImport(RuntimeTypes.Http.Middlware.ResolveEndpoint)
        writer.write("op.install(#T(config.endpointResolver))", RuntimeTypes.Http.Middlware.ResolveEndpoint)
    }
}
