/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes

/**
 * Default endpoint resolver middleware
 */
class ResolveEndpointMiddleware : HttpFeatureMiddleware() {
    override val name: String = "ResolveEndpoint"

    override fun renderConfigure(writer: KotlinWriter) {
        writer.addImport(RuntimeTypes.Http.Middlware.ResolveEndpoint)
        writer.write("resolver = config.endpointResolver")
    }
}
