/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.endpoints.discovery

import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.CodegenContext
import aws.smithy.kotlin.codegen.core.KotlinDelegator
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.applyFileWriter
import aws.smithy.kotlin.codegen.core.clientName
import aws.smithy.kotlin.codegen.core.closeAndOpenBlock
import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.lang.KotlinTypes
import aws.smithy.kotlin.codegen.model.buildSymbol
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.model.expectTrait
import aws.smithy.kotlin.codegen.rendering.endpoints.SdkEndpointBuiltinIntegration
import software.amazon.smithy.aws.traits.clientendpointdiscovery.ClientEndpointDiscoveryTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape

class DefaultEndpointDiscovererGenerator(private val ctx: CodegenContext, private val delegator: KotlinDelegator) {
    private val symbol = symbolFor(ctx.settings)
    private val service = ctx.model.expectShape<ServiceShape>(ctx.settings.service)
    private val clientSymbol = ctx.symbolProvider.toSymbol(service)
    private val operationName = run {
        val epDiscoveryTrait = service.expectTrait<ClientEndpointDiscoveryTrait>()
        val operation = ctx.model.expectShape<OperationShape>(epDiscoveryTrait.operation)
        operation.defaultName()
    }

    companion object {
        fun symbolFor(settings: KotlinSettings): Symbol = buildSymbol {
            val clientName = clientName(settings.sdkId)
            name = "Default${clientName}EndpointDiscoverer"
            namespace = "${settings.pkg.name}.endpoints"
        }
    }

    fun render() {
        delegator.applyFileWriter(symbol) {
            val service = clientName(ctx.settings.sdkId)
            dokka(
                """
                    A class which looks up specific endpoints for $service calls via the `$operationName` API. These
                    unique endpoints are cached as appropriate to avoid unnecessary latency in subsequent calls.
                    @param cache An [ExpiringKeyedCache] implementation used to cache discovered hosts
                """.trimIndent(),
            )

            withBlock(
                "#1L class #2T(#1L val cache: #3T<DiscoveryParams, #4T> = #5T(10.#6T)) : #7T {",
                "}",
                ctx.settings.api.visibility,
                symbol,
                RuntimeTypes.Core.Collections.ExpiringKeyedCache,
                RuntimeTypes.Core.Net.Host,
                RuntimeTypes.Core.Collections.PeriodicSweepCache,
                KotlinTypes.Time.minutes,
                EndpointDiscovererInterfaceGenerator.symbolFor(ctx.settings),
            ) {
                renderAsEndpointResolver()
                write("")
                renderInvalidate()
            }
        }
    }

    private fun KotlinWriter.renderAsEndpointResolver() {
        withBlock(
            "override fun asEndpointResolver(client: #1T, delegate: #2T): #2T = #2T { request ->",
            "}",
            clientSymbol,
            RuntimeTypes.HttpClient.Operation.EndpointResolver,
        ) {
            withBlock("if (client.config.#L == null) {", "}", SdkEndpointBuiltinIntegration.EndpointUrlProp.propertyName) {
                write("val identity = request.identity")
                write(
                    """require(identity is #T) { "Endpoint discovery requires AWS credentials" }""",
                    RuntimeTypes.Auth.Credentials.AwsCredentials.Credentials,
                )
                write("")
                write("val cacheKey = DiscoveryParams(client.config.region, identity.accessKeyId)")
                write("request.context[DiscoveryParamsKey] = cacheKey")
                write("val discoveredHost = cache.get(cacheKey) { discoverHost(client) }")
                write("")
                write("val originalEndpoint = delegate.resolve(request)")
                withBlock("#T(", ")", RuntimeTypes.SmithyClient.Endpoints.Endpoint) {
                    write("originalEndpoint.uri.copy { host = discoveredHost },")
                    write("originalEndpoint.headers,")
                    write("originalEndpoint.attributes,")
                }

                // If user manually specifies endpointUrl, skip endpoint discovery
                closeAndOpenBlock("} else {")
                write("delegate.resolve(request)")
            }
        }
    }

    private fun KotlinWriter.renderInvalidate() {
        withBlock("override public suspend fun invalidate(context: #T) {", "}", RuntimeTypes.Core.ExecutionContext) {
            write("context.getOrNull(DiscoveryParamsKey)?.let { cache.invalidate(it) }")
        }
    }
}
