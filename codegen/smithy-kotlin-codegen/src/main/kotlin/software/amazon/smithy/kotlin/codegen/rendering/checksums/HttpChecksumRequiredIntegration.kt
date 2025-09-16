/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.checksums

import software.amazon.smithy.aws.traits.HttpChecksumTrait
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.HttpChecksumRequiredTrait

/**
 * Handles the `httpChecksumRequired` trait.
 * See: https://smithy.io/2.0/spec/http-bindings.html#httpchecksumrequired-trait
 */
class HttpChecksumRequiredIntegration : KotlinIntegration {
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.isTraitApplied(HttpChecksumRequiredTrait::class.java)

    override fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<ProtocolMiddleware>,
    ): List<ProtocolMiddleware> = resolved + httpChecksumRequiredDefaultAlgorithmMiddleware + httpChecksumRequiredMiddleware
}

/**
 * Adds default checksum algorithm to the execution context
 */
private val httpChecksumRequiredDefaultAlgorithmMiddleware = object : ProtocolMiddleware {
    override val name: String = "httpChecksumRequiredDefaultAlgorithmMiddleware"
    override val order: Byte = -2 // Before S3 Express (possibly) changes the default (-1) and before calculating checksum (0)

    override fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean =
        op.hasTrait<HttpChecksumRequiredTrait>() && !op.hasTrait<HttpChecksumTrait>()

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.write(
            "op.context[#T.DefaultChecksumAlgorithm] = #S",
            RuntimeTypes.HttpClient.Operation.HttpOperationContext,
            "MD5",
        )
    }
}

/**
 * Adds interceptor to calculate request checksums.
 * The `httpChecksum` trait supersedes the `httpChecksumRequired` trait. If both are applied to an operation use `httpChecksum`.
 *
 * See: https://smithy.io/2.0/aws/aws-core.html#behavior-with-httpchecksumrequired
 */
private val httpChecksumRequiredMiddleware = object : ProtocolMiddleware {
    override val name: String = "httpChecksumRequiredMiddleware"

    override fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean =
        op.hasTrait<HttpChecksumRequiredTrait>() && !op.hasTrait<HttpChecksumTrait>()

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.write(
            "op.interceptors.add(#T())",
            RuntimeTypes.HttpClient.Interceptors.HttpChecksumRequiredInterceptor,
        )
    }
}
